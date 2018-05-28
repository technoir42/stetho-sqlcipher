Stetho-SQLCipher
================

DatabaseDriver for [Stetho](https://github.com/facebook/stetho) that allows inspecting SQLCipher-encrypted databases.

## Usage

```
repositories {
    maven { url 'https://dl.bintray.com/sch/maven' }
}

dependencies {
    implementation 'com.sch.stetho:stetho-sqlcipher:1.0.0'
}
```

Install the driver when initializing Stetho:

```java
Stetho.initialize(new Stetho.Initializer(context) {
    @Override
    protected Iterable<DumperPlugin> getDumperPlugins() {
        return new DefaultDumperPluginsBuilder(context).finish();
    }

    @Override
    protected Iterable<ChromeDevtoolsDomain> getInspectorModules() {
        final DatabasePasswordProvider databasePasswordProvider = new DatabasePasswordProvider() {
            @Override
            public String getDatabasePassword(File databaseFile) {
                return "password";
            }
        };
        return new DefaultInspectorModulesBuilder(context)
                .provideDatabaseDriver(new SqlCipherDatabaseDriver(context, databasePasswordProvider))
                .finish();
    }
});
```

## License

```
Copyright 2018 Sergey Chelombitko

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
