# app builds but is currently non functional. It needs transitive runtime
# dependencies.

COMPILE_DEPS = [
    '//lib:CORE_DEPS',
    '//lib:org.apache.karaf.shell.console',
    '//lib:javax.ws.rs-api',
    '//lib:jsch',
    '//utils/rest:onlab-rest',
    '//cli:onos-cli',
    '//core/store/serializers:onos-core-serializers',
    '//apps/dhcp/api:onos-apps-dhcp-api',
    '//apps/xosclient:onos-apps-xosclient',
    '//protocols/ovsdb/api:onos-protocols-ovsdb-api',
    '//protocols/ovsdb/rfc:onos-protocols-ovsdb-rfc',
]

BUNDLES = [
    '//apps/cordvtn:onos-apps-cordvtn',
]

EXCLUDED_BUNDLES = [
    '//lib:jsch',
]

osgi_jar_with_tests (
    deps = COMPILE_DEPS,
    web_context = '/onos/cordvtn',
)

onos_app (
    title = 'CORD VTN REST API',
    category = 'Traffic Steering',
    url = 'http://onosproject.org',
    included_bundles = BUNDLES,
    excluded_bundles = EXCLUDED_BUNDLES,
    description = 'APIs for interacting with the CORD VTN application.',
    required_apps = [ 'org.onosproject.xosclient', 'org.onosproject.dhcp', 'org.onosproject.ovsdb' ],
)
