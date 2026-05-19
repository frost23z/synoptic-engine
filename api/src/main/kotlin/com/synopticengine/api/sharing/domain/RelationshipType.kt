package com.synopticengine.api.sharing.domain

/**
 * A directed edge between two tenants. PARTNER relationships are modelled as two rows
 * (one per direction) because access policies and revocation rights are per-direction.
 *
 * | Type             | Meaning                                                | Revocation rights                                  |
 * |------------------|--------------------------------------------------------|----------------------------------------------------|
 * | PARENT_CHILD     | Owner tenant is structurally above the dependent       | Parent can revoke at will; child cannot            |
 * | PARTNER          | Peer relationship; bidirectional grant negotiated      | Either side may revoke their own grant             |
 * | SUPPLIER_CLIENT  | Source pushes catalog data to consumer                 | Supplier may revoke at will; client may opt out    |
 */
enum class RelationshipType {
    PARENT_CHILD,
    PARTNER,
    SUPPLIER_CLIENT,
}
