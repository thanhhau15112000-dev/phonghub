-- =============================================================================
-- PHONGHUB - BASE DATABASE SCHEMA (POSTGRESQL / FLYWAY V1)
-- =============================================================================
-- Hệ thống Quản lý Phòng trọ (Boarding House & Rental Management)
-- Kiến trúc: Hexagonal Architecture Core Domain
-- Ràng buộc Invariants:
--  1. Tách biệt 4 Roles: LANDLORD_ADMIN, STAFF, TECHNICIAN, TENANT.
--  2. Phân quyền Nhà trọ đa điểm qua bảng staff_property_assignments.
--  3. Bảng giá dịch vụ động (service_rates) và chi tiết hóa đơn (invoice_items).
--  4. Chốt chỉ số điện/nước và hiện trạng tài sản lúc bàn giao (handovers).
--  5. Tách biệt nghĩa vụ nợ (invoices) và dòng tiền thực thu (payments).
-- =============================================================================

-- Kích hoạt tiện ích mở rộng sinh UUID v4
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. NHÓM TÀI KHOẢN & PHÂN QUYỀN (AUTH & SECURITY)
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL CHECK (role IN ('LANDLORD_ADMIN', 'STAFF', 'TECHNICIAN', 'TENANT')),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    address TEXT NOT NULL,
    description TEXT,
    total_rooms INT NOT NULL DEFAULT 0 CHECK (total_rooms >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Quan hệ phân quyền nhân viên theo từng tòa nhà/nhà trọ
CREATE TABLE staff_property_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    can_collect_payment BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_contracts BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_staff_property UNIQUE (staff_user_id, property_id)
);

-- -----------------------------------------------------------------------------
-- 2. NHÓM PHÒNG, TÀI SẢN & BẢNG GIÁ DỊCH VỤ (PROPERTY & ROOMS)
-- -----------------------------------------------------------------------------

CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    room_number VARCHAR(50) NOT NULL,
    floor INT NOT NULL DEFAULT 1,
    area_sqm NUMERIC(6, 2),
    base_price NUMERIC(12, 2) NOT NULL CHECK (base_price >= 0),
    max_occupants INT NOT NULL DEFAULT 2 CHECK (max_occupants > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE', 'RESERVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_room_property_number UNIQUE (property_id, room_number)
);

CREATE TABLE room_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    serial_number VARCHAR(100),
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    condition VARCHAR(255) NOT NULL DEFAULT 'Tốt, hoạt động bình thường',
    estimated_value NUMERIC(12, 2) DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE service_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    service_name VARCHAR(100) NOT NULL,
    charge_type VARCHAR(30) NOT NULL CHECK (charge_type IN ('PER_KWH', 'PER_M3', 'PER_PERSON', 'PER_ROOM_FLAT')),
    price_per_unit NUMERIC(12, 2) NOT NULL CHECK (price_per_unit >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_property_service UNIQUE (property_id, service_name)
);

-- -----------------------------------------------------------------------------
-- 3. NHÓM KHÁCH THUÊ, HỢP ĐỒNG & BÀN GIAO (TENANCY & CONTRACTS)
-- -----------------------------------------------------------------------------

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL, -- NULL nếu khách chưa có tài khoản app
    full_name VARCHAR(150) NOT NULL,
    identity_card_number VARCHAR(20) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    permanent_address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE RESTRICT,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    primary_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    deposit_amount NUMERIC(12, 2) NOT NULL CHECK (deposit_amount >= 0),
    rent_amount NUMERIC(12, 2) NOT NULL CHECK (rent_amount > 0),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    payment_day INT NOT NULL DEFAULT 5 CHECK (payment_day BETWEEN 1 AND 31),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (end_date > start_date)
);

CREATE TABLE contract_occupants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    check_in_date DATE NOT NULL,
    check_out_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_contract_occupant UNIQUE (contract_id, tenant_id)
);

CREATE TABLE handovers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL UNIQUE REFERENCES contracts(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL DEFAULT 'CHECK_IN' CHECK (type IN ('CHECK_IN', 'CHECK_OUT')),
    initial_electric_meter NUMERIC(10, 2) NOT NULL CHECK (initial_electric_meter >= 0),
    initial_water_meter NUMERIC(10, 2) NOT NULL CHECK (initial_water_meter >= 0),
    room_condition TEXT NOT NULL,
    keys_provided TEXT,
    is_confirmed_by_tenant BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 4. NHÓM ĐIỆN NƯỚC, HÓA ĐƠN & DÒNG TIỀN (UTILITIES & BILLING)
-- -----------------------------------------------------------------------------

CREATE TABLE meter_readings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    contract_id UUID REFERENCES contracts(id) ON DELETE SET NULL,
    meter_type VARCHAR(20) NOT NULL CHECK (meter_type IN ('ELECTRIC', 'WATER')),
    previous_value NUMERIC(10, 2) NOT NULL CHECK (previous_value >= 0),
    current_value NUMERIC(10, 2) NOT NULL CHECK (current_value >= previous_value),
    consumption NUMERIC(10, 2) GENERATED ALWAYS AS (current_value - previous_value) STORED,
    reading_date DATE NOT NULL,
    photo_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE billing_cycles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    cycle_month INT NOT NULL CHECK (cycle_month BETWEEN 1 AND 12),
    cycle_year INT NOT NULL CHECK (cycle_year >= 2020),
    due_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'GENERATED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_property_cycle UNIQUE (property_id, cycle_month, cycle_year)
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_cycle_id UUID REFERENCES billing_cycles(id) ON DELETE SET NULL,
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    month INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year INT NOT NULL CHECK (year >= 2020),
    rent_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (rent_amount >= 0),
    utility_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (utility_amount >= 0),
    other_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (other_amount >= 0),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    due_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'PARTIALLY_PAID', 'OVERDUE', 'VOIDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    item_name VARCHAR(150) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL,
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    payment_type VARCHAR(30) NOT NULL DEFAULT 'INVOICE' CHECK (payment_type IN ('DEPOSIT', 'INVOICE', 'REFUND')),
    payment_method VARCHAR(30) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER', 'CASH', 'MOMO', 'VNPAY')),
    transaction_ref VARCHAR(100),
    payment_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'REVERSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 5. NHÓM BẢO TRÌ & QUYẾT TOÁN TRẢ PHÒNG (MAINTENANCE & SETTLEMENT)
-- -----------------------------------------------------------------------------

CREATE TABLE maintenance_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    requested_by_tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
    assigned_technician_id UUID REFERENCES users(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status VARCHAR(30) NOT NULL DEFAULT 'REPORTED' CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'REJECTED')),
    repair_cost NUMERIC(12, 2) DEFAULT 0 CHECK (repair_cost >= 0),
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id UUID NOT NULL UNIQUE REFERENCES contracts(id) ON DELETE CASCADE,
    deposit_amount NUMERIC(12, 2) NOT NULL CHECK (deposit_amount >= 0),
    outstanding_rent NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (outstanding_rent >= 0),
    outstanding_utilities NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (outstanding_utilities >= 0),
    damage_charges NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (damage_charges >= 0),
    other_charges NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (other_charges >= 0),
    refundable_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PREVIEW' CHECK (status IN ('PREVIEW', 'CONFIRMED', 'REFUNDED')),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 6. TỐI ƯU HÓA HIỆU NĂNG TRUY VẤN (INDEXES)
-- -----------------------------------------------------------------------------

CREATE INDEX idx_staff_property_staff ON staff_property_assignments(staff_user_id);
CREATE INDEX idx_staff_property_property ON staff_property_assignments(property_id);

CREATE INDEX idx_rooms_property ON rooms(property_id);
CREATE INDEX idx_rooms_status ON rooms(status);

CREATE INDEX idx_contracts_room ON contracts(room_id);
CREATE INDEX idx_contracts_primary_tenant ON contracts(primary_tenant_id);
CREATE INDEX idx_contracts_status ON contracts(status);

CREATE INDEX idx_occupants_contract ON contract_occupants(contract_id);
CREATE INDEX idx_occupants_tenant ON contract_occupants(tenant_id);

CREATE INDEX idx_meter_readings_room_date ON meter_readings(room_id, reading_date DESC);
CREATE INDEX idx_invoices_contract ON invoices(contract_id);
CREATE INDEX idx_invoices_billing_cycle ON invoices(billing_cycle_id);
CREATE INDEX idx_invoices_status ON invoices(status);

CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payments_contract ON payments(contract_id);

CREATE INDEX idx_maintenance_room ON maintenance_tickets(room_id);
CREATE INDEX idx_maintenance_technician ON maintenance_tickets(assigned_technician_id);
CREATE INDEX idx_maintenance_status ON maintenance_tickets(status);
