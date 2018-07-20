package org.apertereports.util;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apertereports.common.users.User;
import org.apertereports.dao.ReportTemplateDAO;
import org.apertereports.model.ReportTemplate;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.BaseStagedModelDataHandler;
import com.liferay.portal.kernel.lar.ExportImportPathUtil;
import com.liferay.portal.kernel.lar.PortletDataContext;
import com.liferay.portal.kernel.lar.StagedModelDataHandlerUtil;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.model.Company;
import com.liferay.portal.service.CompanyLocalServiceUtil;

import biz.myera.era.model.bo.Mandant;
import biz.myera.era.service.MandantService;
import biz.myera.era.service.PersistentObjectService;
import biz.myera.frmwrk.service.ServiceLocatorUtil;

public class ReportTemplateStagedModelDataHandler extends BaseStagedModelDataHandler<StagedReportTemplateImpl> {

	protected final Log logger = LogFactory.getLog(this.getClass());
	
	public static final String[] CLASS_NAMES = {ReportTemplate.class.getName()};
	@Override
	public void deleteStagedModel(String uuid, long groupId, String className, String extraData)
			throws PortalException, SystemException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String[] getClassNames() {
		return CLASS_NAMES;
	}

	@Override
	protected void doExportStagedModel(PortletDataContext portletDataContext, StagedReportTemplateImpl stagedModel)
			throws Exception {
       Element productElement = portletDataContext.getExportDataElement(stagedModel);
       portletDataContext.addClassedModel(productElement, ExportImportPathUtil.getModelPath(stagedModel), stagedModel);		
	}

	@Override
	protected void doImportStagedModel(PortletDataContext portletDataContext, StagedReportTemplateImpl stagedModel)
			throws Exception {
		logger.info("import Model: " + stagedModel.getModelClassName());    						
    	
		ReportTemplate reportTemplate = stagedModel.getAperteReport(String.valueOf(portletDataContext.getCompanyId()));
		
		//check if report with same name exists
		Collection<ReportTemplate> reportTemplatesWithSameName = ReportTemplateDAO.fetchByName(AperteReportsDataHandler.getAperteUser(), reportTemplate.getReportname());
		for (ReportTemplate rt : reportTemplatesWithSameName) {
			logger.info("Report with same name found. Deleting report with id " + rt.getId());
			ReportTemplateDAO.remove(rt);
		}
								
		ReportTemplateDAO.saveOrUpdate(reportTemplate);		
	}

}
