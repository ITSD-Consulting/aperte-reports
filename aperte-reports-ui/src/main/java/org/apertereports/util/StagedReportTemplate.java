package org.apertereports.util;

import org.apertereports.model.ReportTemplate;
import org.springframework.dao.DataAccessException;

import com.liferay.portal.model.StagedModel;

import biz.myera.era.model.bo.Mandant;


public interface StagedReportTemplate extends StagedModel{

	public ReportTemplate getAperteReport(String companyId) throws DataAccessException;	
}
