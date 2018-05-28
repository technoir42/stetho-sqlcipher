package com.sch.stetho.sqlcipher;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;

public interface DatabaseConnectionProvider {
    SQLiteDatabase openDatabase(File databaseFile) throws SQLException;
}
