package com.sch.stetho.sqlcipher;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import com.facebook.stetho.common.LogUtil;
import com.facebook.stetho.inspector.database.DatabaseFilesProvider;
import com.facebook.stetho.inspector.database.DefaultDatabaseFilesProvider;
import com.facebook.stetho.inspector.protocol.module.Database;
import com.facebook.stetho.inspector.protocol.module.DatabaseDescriptor;
import com.facebook.stetho.inspector.protocol.module.DatabaseDriver2;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SqlCipherDatabaseDriver extends DatabaseDriver2<SqlCipherDatabaseDriver.SqlCipherDatabaseDescriptor> {
    private static final byte[] SQLITE_MAGIC_BYTES = "SQLite format 3\000".getBytes();

    private static final String[] UNINTERESTING_FILENAME_SUFFIXES = {
            "-journal",
            "-shm",
            "-uid",
            "-wal"
    };

    private final DatabaseFilesProvider databaseFilesProvider;
    private final DatabaseConnectionProvider databaseConnectionProvider;

    public SqlCipherDatabaseDriver(Context context, DatabasePasswordProvider databasePasswordProvider) {
        this(context, new DefaultDatabaseFilesProvider(context), new DefaultDatabaseConnectionProvider(databasePasswordProvider));
    }

    public SqlCipherDatabaseDriver(Context context, DatabaseFilesProvider databaseFilesProvider, DatabaseConnectionProvider databaseConnectionProvider) {
        super(context);
        this.databaseFilesProvider = databaseFilesProvider;
        this.databaseConnectionProvider = databaseConnectionProvider;
    }

    @Override
    public List<SqlCipherDatabaseDescriptor> getDatabaseNames() {
        List<File> potentialDatabaseFiles = databaseFilesProvider.getDatabaseFiles();
        Collections.sort(potentialDatabaseFiles);
        List<File> tidiedList = tidyDatabaseList(potentialDatabaseFiles);

        List<SqlCipherDatabaseDescriptor> databases = new ArrayList<>(tidiedList.size());
        for (File databaseFile : tidiedList) {
            if (checkFileHeader(databaseFile)) {
                databases.add(new SqlCipherDatabaseDescriptor(databaseFile));
            }
        }
        return databases;
    }

    @Override
    public List<String> getTableNames(SqlCipherDatabaseDescriptor databaseDesc) {
        SQLiteDatabase database;
        try {
            database = openDatabase(databaseDesc);
        } catch (RuntimeException e) {
            throw new SQLiteException("Unable to open database", e);
        }
        try {
            Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type IN (?, ?)", new String[]{"table", "view"});
            try {
                List<String> tableNames = new ArrayList<>(cursor.getCount());
                while (cursor.moveToNext()) {
                    tableNames.add(cursor.getString(0));
                }
                return tableNames;
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
    }

    @Override
    public Database.ExecuteSQLResponse executeSQL(SqlCipherDatabaseDescriptor databaseDesc, String query, ExecuteResultHandler<Database.ExecuteSQLResponse> handler) {
        SQLiteDatabase database = openDatabase(databaseDesc);
        try {
            String firstWordUpperCase = getFirstWord(query).toUpperCase(Locale.ROOT);
            switch (firstWordUpperCase) {
                case "UPDATE":
                case "DELETE":
                    return executeUpdateDelete(database, query, handler);
                case "INSERT":
                    return executeInsert(database, query, handler);
                case "SELECT":
                case "PRAGMA":
                case "EXPLAIN":
                    return executeSelect(database, query, handler);
                default:
                    return executeRawQuery(database, query, handler);
            }
        } finally {
            database.close();
        }
    }

    private <T> T executeUpdateDelete(SQLiteDatabase database, String query, ExecuteResultHandler<T> handler) {
        SQLiteStatement statement = database.compileStatement(query);
        int count = statement.executeUpdateDelete();
        return handler.handleUpdateDelete(count);
    }

    private <T> T executeInsert(SQLiteDatabase database, String query, ExecuteResultHandler<T> handler) {
        SQLiteStatement statement = database.compileStatement(query);
        long count = statement.executeInsert();
        return handler.handleInsert(count);
    }

    private <T> T executeSelect(SQLiteDatabase database, String query, ExecuteResultHandler<T> handler) {
        Cursor cursor = database.rawQuery(query, null);
        try {
            return handler.handleSelect(cursor);
        } finally {
            cursor.close();
        }
    }

    private <T> T executeRawQuery(SQLiteDatabase database, String query, ExecuteResultHandler<T> handler) {
        database.execSQL(query);
        return handler.handleRawQuery();
    }

    private SQLiteDatabase openDatabase(SqlCipherDatabaseDescriptor databaseDesc) {
        return databaseConnectionProvider.openDatabase(databaseDesc.file);
    }

    private static List<File> tidyDatabaseList(List<File> databaseFiles) {
        Set<File> originalAsSet = new HashSet<>(databaseFiles);
        List<File> tidiedList = new ArrayList<>();
        for (File databaseFile : databaseFiles) {
            String databaseFilename = databaseFile.getPath();
            String sansSuffix = removeSuffix(databaseFilename, UNINTERESTING_FILENAME_SUFFIXES);
            if (sansSuffix.equals(databaseFilename) || !originalAsSet.contains(new File(sansSuffix))) {
                tidiedList.add(databaseFile);
            }
        }
        return tidiedList;
    }

    private static String removeSuffix(String str, String[] suffixesToRemove) {
        for (String suffix : suffixesToRemove) {
            if (str.endsWith(suffix)) {
                return str.substring(0, str.length() - suffix.length());
            }
        }
        return str;
    }

    private static String getFirstWord(String s) {
        s = s.trim();
        int firstSpace = s.indexOf(' ');
        return firstSpace >= 0 ? s.substring(0, firstSpace) : s;
    }

    private boolean checkFileHeader(File databaseFile) {
        FileInputStream input = null;
        try {
            input = new FileInputStream(databaseFile);
            final byte[] magic = new byte[16];
            input.read(magic);
            return !Arrays.equals(magic, SQLITE_MAGIC_BYTES);
        } catch (IOException e) {
            LogUtil.e(e, "Unable to open database file");
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static final class SqlCipherDatabaseDescriptor implements DatabaseDescriptor {
        final File file;

        SqlCipherDatabaseDescriptor(File file) {
            this.file = file;
        }

        @Override
        public String name() {
            return file.getName() + " (SQLCipher)";
        }
    }
}
