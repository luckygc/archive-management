package github.luckygc.am.infrastructure.runtime;

import github.luckygc.am.common.runtime.RuntimeDatabaseAdapter;

class PostgreSqlRuntimeDatabaseAdapter implements RuntimeDatabaseAdapter {

    @Override
    public String adapter() {
        return "postgresql";
    }
}
