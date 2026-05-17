package com.synopticengine.api.crm.scoping

import com.synopticengine.api.identity.IdentityApi
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves the set of user ids the current request may see records for, based on the
 * authenticated user's view permission (GLOBAL / GROUP / INDIVIDUAL).
 *
 * Returns `null` when the user has unrestricted access (GLOBAL/ALL) — callers should
 * skip filtering in that case.
 *
 * Centralised so every list/search/filter call site can opt-in consistently. The
 * previous duplicate `resolveScope()` private functions in LeadService / PersonService /
 * OrganizationService / QuoteService all collapse to a single call here.
 */
@Component
class ScopeResolver(
    private val identityApi: IdentityApi,
) {
    fun userIdsForCurrentUser(): Set<UUID>? {
        val email = SecurityContextHolder.getContext().authentication?.name ?: return null
        return identityApi.resolveViewContextByEmail(email).userIds
    }
}
