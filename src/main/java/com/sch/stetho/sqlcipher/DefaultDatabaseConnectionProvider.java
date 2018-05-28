package com.sch.stetho.sqlcipher;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;

public class DefaultDatabaseConnectionProvider implements DatabaseConnectionProvider {
    private final DatabasePasswordProvider databasePasswordProvider;

    public DefaultDatabaseConnectionProvider(DatabasePasswordProvider databasePasswordProvider) {
        this.databasePasswordProvider = databasePasswordProvider;
    }

    @Override
    public SQLiteDatabase openDatabase(File databaseFile) throws SQLException {
        File walFile = new File(databaseFile.getParent(), databaseFile.getName() + "-wal");

        final SQLiteDatabase db = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(),
                databasePasswordProvider.getDatabasePassword(databaseFile),
                null,
                SQLiteDatabase.OPEN_READWRITE);

        db.rawExecSQL("PRAGMA foreign_keys=ON;");

        if (walFile.exists()) {
            db.rawExecSQL("PRAGMA journal_mode=WAL;");
        }
        return db;
    }
}
