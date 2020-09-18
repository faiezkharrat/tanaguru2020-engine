package com.tanaguru.service;

import com.tanaguru.domain.constant.EAuditParameter;
import com.tanaguru.domain.constant.EAuditType;
import com.tanaguru.domain.constant.EParameterFamily;
import com.tanaguru.domain.entity.audit.parameter.AuditParameter;
import com.tanaguru.domain.entity.audit.parameter.AuditParameterFamily;
import com.tanaguru.domain.entity.audit.parameter.AuditParameterValue;
import com.tanaguru.domain.entity.membership.project.Project;

import java.util.Collection;
import java.util.Map;

/**
 * @author rcharre
 */
public interface AuditParameterService {

    /**
     * Get a default parameter map for a given audit type
     *
     * @param auditType EAuditTypes
     * @return A Map of default AuditParameter and AuditParameterValue
     */
    Map<AuditParameter, AuditParameterValue> getDefaultParameterForAuditType(EAuditType auditType);


    /**
     * @return auditParametersByFamilyName
     */
    Map<EParameterFamily, Collection<AuditParameter>> getAuditParametersByFamilyName();

    /**
     * @return auditParameterFamiliesMap
     */
    Map<EParameterFamily, AuditParameterFamily> getAuditParameterFamiliesMap();

    /**
     * @param parameterFamily @see EParameterFamily
     * @return A @see  AuditParameter @see Collection for a giver @see  EParameterFamily
     */
    Collection<AuditParameter> getAuditParametersForFamily(EParameterFamily parameterFamily);

    /**
     * Retrieve default @see AuditParameter and @see AuditParameterValue , check and apply override values for a given @see EAuditType
     *
     * @param auditType The @see EAuditType
     * @param override  The @see AuditParameter to override
     * @param project  The @see Project parameters are applied to
     * @return A map containing @see AuditParameter and @see ParameterValues
     */
    Map<AuditParameter, AuditParameterValue> getParameterMapForAuditTypeWithParameterOverride(EAuditType auditType, Map<EAuditParameter, String> override, Project project);
}
