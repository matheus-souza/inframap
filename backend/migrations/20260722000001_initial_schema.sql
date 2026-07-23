-- +goose Up

-- =============================================================================
-- InfraMap Initial Schema Migration — RFC-006 Compliant
-- Creates all 15 core tables across 5 domain modules.
-- =============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Trigger function for automatic updated_at timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- =============================================================================
-- Module 0: Configuration — System Initialization & Onboarding
-- =============================================================================

-- 1. system_state
CREATE TABLE system_state (
    id UUID PRIMARY KEY,
    onboarding_completed BOOLEAN NOT NULL DEFAULT false,
    onboarding_completed_at TIMESTAMPTZ,
    system_instance_id UUID NOT NULL,
    telemetry_enabled BOOLEAN NOT NULL DEFAULT false,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_system_state_updated_at
    BEFORE UPDATE ON system_state
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Module 1: Identity & Access Management
-- =============================================================================

-- 2. users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 3. roles
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(64) UNIQUE NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_roles_name ON roles(name);

CREATE TRIGGER update_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 4. permissions
CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(64) UNIQUE NOT NULL,
    resource VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permissions_resource ON permissions(resource);

-- 5. role_permissions (N:M)
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

-- 6. user_roles (N:M)
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- 7. user_sessions
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    user_agent TEXT,
    ip_address INET,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_token_hash ON user_sessions(token_hash);

-- =============================================================================
-- Module 2: Infrastructure Inventory
-- =============================================================================

-- 8. devices
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip_address INET,
    mac_address MACADDR,
    manufacturer VARCHAR(128),
    model VARCHAR(128),
    serial_number VARCHAR(128),
    device_type VARCHAR(64) NOT NULL DEFAULT 'unknown',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_hostname ON devices(hostname);
CREATE INDEX idx_devices_ip_address ON devices(ip_address);
CREATE INDEX idx_devices_mac_address ON devices(mac_address);
CREATE INDEX idx_devices_device_type ON devices(device_type);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_deleted_at ON devices(deleted_at);
CREATE INDEX idx_devices_metadata ON devices USING gin (metadata);

CREATE TRIGGER update_devices_updated_at
    BEFORE UPDATE ON devices
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 9. network_interfaces
CREATE TABLE network_interfaces (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    mac_address MACADDR,
    vlan_id INT,
    speed_mbps INT,
    is_virtual BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(32) NOT NULL DEFAULT 'up',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_interface UNIQUE (device_id, name)
);

CREATE INDEX idx_network_interfaces_device_id ON network_interfaces(device_id);
CREATE INDEX idx_network_interfaces_mac ON network_interfaces(mac_address);

CREATE TRIGGER update_network_interfaces_updated_at
    BEFORE UPDATE ON network_interfaces
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 10. ip_addresses
CREATE TABLE ip_addresses (
    id UUID PRIMARY KEY,
    interface_id UUID REFERENCES network_interfaces(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    address INET NOT NULL,
    family VARCHAR(8) NOT NULL DEFAULT 'v4',
    assignment_type VARCHAR(32) NOT NULL DEFAULT 'dhcp',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_ip UNIQUE (device_id, address)
);

CREATE INDEX idx_ip_addresses_address ON ip_addresses(address);
CREATE INDEX idx_ip_addresses_device_id ON ip_addresses(device_id);

-- 11. subnets
CREATE TABLE subnets (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    cidr CIDR NOT NULL UNIQUE,
    vlan_id INT,
    gateway_ip INET,
    description TEXT,
    discovery_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subnets_cidr ON subnets USING gist (cidr inet_ops);

CREATE TRIGGER update_subnets_updated_at
    BEFORE UPDATE ON subnets
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Module 3: Network Topology & Relationships
-- =============================================================================

-- 12. topology_links
CREATE TABLE topology_links (
    id UUID PRIMARY KEY,
    source_device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    target_device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    source_interface_id UUID REFERENCES network_interfaces(id) ON DELETE SET NULL,
    target_interface_id UUID REFERENCES network_interfaces(id) ON DELETE SET NULL,
    link_type VARCHAR(64) NOT NULL,
    confidence_score NUMERIC(3, 2) NOT NULL DEFAULT 1.00,
    discovered_by VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_confidence_range CHECK (confidence_score >= 0.00 AND confidence_score <= 1.00)
);

CREATE INDEX idx_topology_links_source ON topology_links(source_device_id);
CREATE INDEX idx_topology_links_target ON topology_links(target_device_id);
CREATE INDEX idx_topology_links_type ON topology_links(link_type);

CREATE TRIGGER update_topology_links_updated_at
    BEFORE UPDATE ON topology_links
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Module 4: Discovery Engine & Provenance
-- =============================================================================

-- 13. discovery_sources
CREATE TABLE discovery_sources (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    schedule_cron VARCHAR(64),
    config_encrypted TEXT,
    last_run_at TIMESTAMPTZ,
    last_status VARCHAR(32) NOT NULL DEFAULT 'idle',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_discovery_sources_type ON discovery_sources(type);

CREATE TRIGGER update_discovery_sources_updated_at
    BEFORE UPDATE ON discovery_sources
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 14. device_discovery_records
CREATE TABLE device_discovery_records (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    discovery_source_id UUID NOT NULL REFERENCES discovery_sources(id) ON DELETE CASCADE,
    matched_by VARCHAR(32) NOT NULL,
    raw_payload JSONB NOT NULL,
    last_scanned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_source UNIQUE (device_id, discovery_source_id)
);

CREATE INDEX idx_discovery_records_device ON device_discovery_records(device_id);
CREATE INDEX idx_discovery_records_payload ON device_discovery_records USING gin (raw_payload);

-- =============================================================================
-- Module 5: Audit & Platform Logs
-- =============================================================================

-- 15. audit_logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_name VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID,
    changes JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- +goose Down
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS device_discovery_records;
DROP TABLE IF EXISTS discovery_sources;
DROP TABLE IF EXISTS topology_links;
DROP TABLE IF EXISTS subnets;
DROP TABLE IF EXISTS ip_addresses;
DROP TABLE IF EXISTS network_interfaces;
DROP TABLE IF EXISTS devices;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS system_state;
DROP FUNCTION IF EXISTS update_updated_at_column();
