package org.apereo.cas.support.saml.mdui.web.flow;

import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.UnauthorizedServiceException;
import org.apereo.cas.support.saml.mdui.MetadataResolverAdapter;
import org.apereo.cas.support.saml.mdui.MetadataUIUtils;
import org.apereo.cas.support.saml.mdui.SamlMetadataUIInfo;
import org.apereo.cas.web.support.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import javax.servlet.http.HttpServletRequest;

/**
 * This is {@link SamlMetadataUIParserAction} that attempts to parse
 * the MDUI extension block for a SAML SP from the provided metadata locations.
 * The result is put into the flow request context. The entity id parameter is
 * specified by default at {@link org.apereo.cas.support.saml.SamlProtocolConstants#PARAMETER_ENTITY_ID}.
 * <p>This action is best suited to be invoked when the CAS login page
 * is about to render so that the page, once the MDUI info is obtained,
 * has a chance to populate the UI with relevant info about the SP.</p>
 *
 * @author Misagh Moayyed
 * @since 4.1.0
 */
public class SamlMetadataUIParserAction extends AbstractAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SamlMetadataUIParserAction.class);

    private final String entityIdParameterName;
    private final MetadataResolverAdapter metadataAdapter;

    private ServicesManager servicesManager;
    private ServiceFactory<WebApplicationService> serviceFactory;

    /**
     * Instantiates a new SAML MDUI parser action.
     *
     * @param entityIdParameterName the entity id parameter name
     * @param metadataAdapter       the metadata adapter
     * @param serviceFactory        the service factory
     * @param servicesManager       the service manager
     */
    public SamlMetadataUIParserAction(final String entityIdParameterName, final MetadataResolverAdapter metadataAdapter,
                                      final ServiceFactory<WebApplicationService> serviceFactory, final ServicesManager servicesManager) {
        this.entityIdParameterName = entityIdParameterName;
        this.metadataAdapter = metadataAdapter;
        this.serviceFactory = serviceFactory;
        this.servicesManager = servicesManager;
    }

    @Override
    public Event doExecute(final RequestContext requestContext) throws Exception {
        final HttpServletRequest request = WebUtils.getHttpServletRequest(requestContext);
        final String entityId = request.getParameter(this.entityIdParameterName);
        if (StringUtils.isBlank(entityId)) {
            LOGGER.debug("No entity id found for parameter [{}]", this.entityIdParameterName);
            return success();
        }
        final WebApplicationService service = this.serviceFactory.createService(entityId);
        final RegisteredService registeredService = this.servicesManager.findServiceBy(service);
        if (registeredService == null || !registeredService.getAccessStrategy().isServiceAccessAllowed()) {
            LOGGER.debug("Entity id [{}] is not recognized/allowed by the CAS service registry", entityId);

            if (registeredService != null) {
                WebUtils.putUnauthorizedRedirectUrlIntoFlowScope(requestContext,
                        registeredService.getAccessStrategy().getUnauthorizedRedirectUrl());
            }
            throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE, "Entity [" + entityId + "] not recognized");
        }

        final SamlMetadataUIInfo mdui = MetadataUIUtils.locateMetadataUserInterfaceForEntityId(this.metadataAdapter, entityId, registeredService);
        WebUtils.putServiceUserInterfaceMetadata(requestContext, mdui);
        return success();
    }
}
