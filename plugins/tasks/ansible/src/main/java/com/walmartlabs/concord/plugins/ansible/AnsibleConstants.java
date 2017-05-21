package com.walmartlabs.concord.plugins.ansible;

public final class AnsibleConstants {

    public static final String INVENTORY_FILE_NAME = "_inventory";

    public static final String DYNAMIC_INVENTORY_FILE_NAME = "_dynamicInventory";

    public static final String PRIVATE_KEY_FILE_NAME = "_privateKey";

    public static final String VAULT_PASSWORD_KEY = "vaultPassword";

    public static final String VAULT_PASSWORD_FILE_PATH = "_vaultPassword";

    public static final String CONFIG_KEY = "config";

    public static final String INVENTORY_KEY = "inventory";

    public static final String EXTRA_VARS_KEY = "extraVars";

    public static final String PLAYBOOK_KEY = "playbook";

    public static final String STATS_FILE_NAME = "ansible_stats.json";

    public static final String EXIT_CODE_KEY = "exitCode";

    private AnsibleConstants() {
    }
}
