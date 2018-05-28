package com.sch.stetho.sqlcipher;

import java.io.File;

public interface DatabasePasswordProvider {
    String getDatabasePassword(File databaseFile);
}
