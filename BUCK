SRC = 'src/main/java/org/onosproject/**/'
TEST = 'src/main/java/org/onosproject/**/'

CURRENT_NAME = 'onos-app-cordvtn'
CURRENT_TARGET = ':' + CURRENT_NAME

COMPILE_DEPS = [
    '//lib:CORE_DEPS',
    '//lib:org.apache.karaf.shell.console',
    '//lib:javax.ws.rs-api',
    '//lib:jsch',
    '//utils/rest:onlab-rest',
    '//cli:onos-cli',
    '//core/store/serializers:onos-core-serializers',
    '//apps/openstackinterface:onos-app-openstackinterface-api',
    '//apps/dhcp/api:onos-app-dhcp-api',
    '//protocols/ovsdb/api:onos-ovsdb-api',
    '//protocols/ovsdb/rfc:onos-ovsdb-rfc',
]

TEST_DEPS = [
    '//lib:TEST',
]

java_library(
    name = CURRENT_NAME,
    srcs = glob([SRC + '/*.java']),
    deps = COMPILE_DEPS,
    visibility = ['PUBLIC'],
    resources_root = 'src/main/resources',
    resources = glob(['src/main/resources/**']),
)

java_test(
    name = 'tests',
    srcs = glob([TEST + '/*.java']),
    deps = COMPILE_DEPS +
           TEST_DEPS +
           [CURRENT_TARGET],
    source_under_test = [CURRENT_TARGET],
)
