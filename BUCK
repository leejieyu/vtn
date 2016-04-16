COMPILE_DEPS = [
    '//lib:CORE_DEPS',
    '//lib:org.apache.karaf.shell.console',
    '//lib:javax.ws.rs-api',
    '//lib:jsch',
    '//lib:openstack4j-core',
    '//lib:openstack4j-http-connector',
    '//lib:openstack4j-httpclient',
    '//utils/rest:onlab-rest',
    '//cli:onos-cli',
    '//core/store/serializers:onos-core-serializers',
    '//apps/openstackinterface:onos-apps-openstackinterface-api',
    '//apps/dhcp/api:onos-apps-dhcp-api',
    '//protocols/ovsdb/api:onos-ovsdb-api',
    '//protocols/ovsdb/rfc:onos-ovsdb-rfc',
]

BUNDLES = [
    '//apps/openstackinterface:onos-apps-openstackinterface-api',
    '//apps/cordvtn:onos-apps-cordvtn',
]

EXCLUDED_BUNDLES = [
    '//lib:jsch', # TODO - needs wrap
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
)
