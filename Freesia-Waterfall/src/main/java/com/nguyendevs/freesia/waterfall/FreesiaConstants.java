package com.nguyendevs.freesia.waterfall;

import java.io.File;

public class FreesiaConstants {
    public static final class FileConstants {
        public static final File PLUGINS_DIR = new File("plugins");
        public static final File PLUGIN_DIR = new File(PLUGINS_DIR, "Freesia");

        public static final File CONFIG_FILE = new File(PLUGIN_DIR, "freesia_config.toml");
        public static final File SECURITY_CONFIG_FILE = new File(PLUGIN_DIR, "security.toml");

        public static final File PLAYER_DATA_DIR = new File(PLUGIN_DIR, "playerdata");
        public static final File VIRTUAL_PLAYER_DATA_DIR = new File(PLUGIN_DIR, "playerdata_virtual");

        static {
            PLUGIN_DIR.mkdirs();
            PLAYER_DATA_DIR.mkdirs();
            VIRTUAL_PLAYER_DATA_DIR.mkdirs();
        }
    }

    public static final class PermissionConstants {
        public static final String LIST_PLAYER_COMMAND = "freesia.commands.listysmplayers",
                DISPATCH_WORKER_COMMAND = "freesia.commands.dworkerc",
                SET_SKIN_COMMAND = "freesia.commands.setskin",
                RELOAD_COMMAND = "freesia.commands.reload";
    }

    public static final class LanguageConstants {
        public static final String WORKER_NOT_FOUND = "freesia.worker_command.worker_not_found",
                WORKER_COMMAND_FEEDBACK = "freesia.worker_command.command_feedback",

                PLAYER_LIST_HEADER = "freesia.list_player_command_header",
                PLAYER_LIST_ENTRY = "freesia.list_player_command_body",

                SETSKIN_SUCCESS = "freesia.setskin.success",
                SETSKIN_NO_PLAYERS = "freesia.setskin.no_players",
                SETSKIN_CITIZENS_DISABLED = "freesia.setskin.citizens_disabled",

                HANDSHAKE_TIMED_OUT = "freesia.mod_handshake_time_outed",
                WORKER_TERMINATED_CONNECTION = "freesia.backend.disconnected",
                WORKER_NOT_CONNECTED = "freesia.backend.not_connected";

    }

    public static final class MCProtocolConstants {
        public static final int PROTOCOL_NUM_V1202 = 764;
    }

    public static class YsmProtocolMetaConstants {
        public static final class Serverbound {
            public static final String HAND_SHAKE_REQUEST = "handshake_request";
            public static final String MOLANG_EXECUTE_REQ = "molang_execute_req";
            public static final String ANIMATION_REQ = "animation_req";
        }

        public static final class Clientbound {
            public static final String HAND_SHAKE_CONFIRMED = "handshake_confirmed";

            public static final String ENTITY_DATA_UPDATE = "entity_data_update";

            public static final String MOLANG_EXECUTE = "molang_execute";

            public static final String ANIMATION = "animation";
        }
    }
}

