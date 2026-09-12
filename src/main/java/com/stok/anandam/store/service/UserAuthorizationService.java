package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.DefaultRolePermissions;
import com.stok.anandam.store.core.postgres.model.Role;
import com.stok.anandam.store.core.postgres.model.User;
import com.stok.anandam.store.core.postgres.model.Permission;
import com.stok.anandam.store.core.postgres.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared server-side authorization helpers for transaction-scoped features
 * (Sales, Purchase, Piutang, Location).
 *
 * Scope rule (business decision): a user is restricted to an EMP_Code scope
 * ONLY when the account has a non-empty {@code empCodeAccess}. Users without a
 * configured empCodeAccess keep today's full visibility. MANAGER/ADMIN normally
 * carry no empCodeAccess and therefore stay unrestricted.
 */
@Service
public class UserAuthorizationService {

    @Autowired
    private UserRepository userRepository;

    /** Resolve the authenticated actor by username, or fail with 404. */
    public User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
    }

    /**
     * Effective permission set of a user, mirroring CustomUserDetailsService:
     * MANAGER/ADMIN always get every permission; other roles use the stored
     * permission set and fall back to the role default template when empty.
     */
    public Set<String> effectivePermissions(User user) {
        if (user == null) {
            return java.util.Collections.emptySet();
        }
        Role role = user.getRole();
        if (role == Role.MANAGER || role == Role.ADMIN) {
            return DefaultRolePermissions.getAllPermissions();
        }
        Set<String> perms = user.getPermissions();
        if (perms == null || perms.isEmpty()) {
            perms = DefaultRolePermissions.getDefaultPermissions(role);
        }
        return perms != null ? perms : java.util.Collections.emptySet();
    }

    public boolean hasPermission(User user, String permission) {
        return permission != null && effectivePermissions(user).contains(permission);
    }

    /** Whether this user may see paid/unpaid payment status on transactions. */
    public boolean canViewPaymentStatus(User user) {
        return hasPermission(user, Permission.PAYMENT_STATUS);
    }

    /**
     * Authorized EMP codes for a user, or {@code null} when unrestricted.
     * Restriction exists only when empCodeAccess is non-empty; the user's own
     * employeeCode is always added. Values are passed verbatim (they originate
     * from the same sales-table distinct codes), plus case variants of the own
     * employeeCode so a mixed-case own code still matches the stored value.
     */
    public List<String> allowedEmpCodes(User user) {
        if (user == null) {
            return null;
        }
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER) {
            return null;
        }

        Set<String> access = user.getEmpCodeAccess();
        String own = user.getEmployeeCode();

        boolean hasAccess = access != null && !access.isEmpty();
        boolean hasOwn = own != null && !own.trim().isEmpty();

        if (!hasAccess && !hasOwn) {
            return null;
        }

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if (hasAccess) {
            for (String code : access) {
                if (code != null && !code.trim().isEmpty()) {
                    addCaseVariants(allowed, code.trim());
                }
            }
        }
        if (hasOwn) {
            addCaseVariants(allowed, own.trim());
        }
        return new ArrayList<>(allowed);
    }

    /** Tambahkan varian asli/uppercase/lowercase supaya cocok dgn kolom emp_code case-sensitive. */
    private void addCaseVariants(LinkedHashSet<String> set, String code) {
        set.add(code);
        set.add(code.toUpperCase(Locale.ROOT));
        set.add(code.toLowerCase(Locale.ROOT));
    }

    /**
     * Merge the caller-requested emp-code filter with the user's allowed scope.
     * Unrestricted users simply keep their requested filter (may be null = all).
     * Restricted users see only the intersection of their scope and the request;
     * an empty intersection yields an empty list (no rows).
     */
    public List<String> effectiveScopeFilter(User user, List<String> requestedEmpCodes) {
        List<String> allowed = allowedEmpCodes(user);
        if (allowed == null) {
            return requestedEmpCodes;
        }
        if (requestedEmpCodes == null || requestedEmpCodes.isEmpty()) {
            return allowed;
        }
        Set<String> requestedNorm = new LinkedHashSet<>();
        for (String code : requestedEmpCodes) {
            if (code != null && !code.trim().isEmpty()) {
                requestedNorm.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (requestedNorm.isEmpty()) {
            return allowed;
        }
        // Return varian dari allowed (bukan request) agar nilai emp_code sesuai kolom DB,
        // hanya untuk kode yang juga diminta user (intersection case-insensitive).
        List<String> intersection = new ArrayList<>();
        for (String code : allowed) {
            if (requestedNorm.contains(code.toUpperCase(Locale.ROOT)) && !intersection.contains(code)) {
                intersection.add(code);
            }
        }
        return intersection;
    }
}
