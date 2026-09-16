/**
 * Public audit contracts and the internal audit implementation.
 *
 * <p>The root package is the audit module's supported boundary. Query callers
 * depend only on the immutable contracts in this package; persistence,
 * listeners, and provider/framework types remain in internal subpackages.</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Business Audit")
package io.github.guillermodubon.coachgym.audit;
