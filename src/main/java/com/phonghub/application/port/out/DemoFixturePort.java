package com.phonghub.application.port.out;

import java.util.UUID;

public interface DemoFixturePort {
    UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID OWNER_1_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");
    UUID OWNER_2_ID = UUID.fromString("55555555-0000-0000-0000-000000000002");
    UUID STAFF_1_ID = UUID.fromString("22222222-2222-2222-2222-222222222221");
    UUID STAFF_2_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    UUID TECH_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333331");
    UUID TENANT_1_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");

    UUID PROP_1_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    UUID PROP_2_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    UUID PROP_3_ID = UUID.fromString("aaaaaaaa-3333-3333-3333-333333333333");
}
