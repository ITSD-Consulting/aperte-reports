package org.apertereports.bean;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apertereports.backbone.util.ReportTemplateProvider;
import org.apertereports.common.ARConstants;
import org.apertereports.common.exception.ARException;
import org.apertereports.common.users.User;
import org.apertereports.common.users.UserRole;
import org.apertereports.common.wrappers.DictionaryItem;
import org.apertereports.dao.DictionaryDAO;
import org.apertereports.dao.ReportTemplateDAO;
import org.apertereports.dao.utils.ConfigurationCache;
import org.apertereports.engine.ReportMaster;
import org.apertereports.engine.ReportParameter;
import org.apertereports.engine.ReportProperty;
import org.apertereports.model.ReportTemplate;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Organization;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.model.UserGroupGroupRole;
import com.liferay.portal.model.UserGroupRole;
import com.liferay.portal.security.auth.AuthTokenUtil;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.UserGroupGroupRoleLocalServiceUtil;
import com.liferay.portal.service.UserGroupRoleLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;

@ManagedBean(name = "reportInvokerBean")
@SessionScoped
public class ReportInvokerBean {

	private static final String REPORT_TYPE_KEY = "report_type";
	private static final String TYP_PDF = "PDF";
	private static final String TYP_XLS = "XLS";

	private List<ReportTemplate> reportTemplates = new ArrayList<ReportTemplate>();
	private Map<Integer, ReportMaster> reportMasters = new HashMap<Integer, ReportMaster>();
	private Map<Integer, Map<String, Object>> reportParameters = new HashMap<Integer, Map<String, Object>>();
	private User user;

	public ReportInvokerBean() {
		PortletRequest request = (PortletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();

		initAperteUser(request);

		reportTemplates.addAll(ReportTemplateDAO.fetchActive(user));
	}

	/*
	 * Initialisiert den User und setzt seine Rollen und andere benötigte Parameter
	 */
	private void initAperteUser(PortletRequest request) {
		try {
			com.liferay.portal.model.User liferayUser = PortalUtil.getUser(request);
			com.liferay.portal.model.Company company = PortalUtil.getCompany(request);
			com.liferay.portal.theme.ThemeDisplay dis = (com.liferay.portal.theme.ThemeDisplay) request
					.getAttribute(com.liferay.portal.kernel.util.WebKeys.THEME_DISPLAY);
			// liferay user can be null because he can be not logged in
			if (liferayUser != null) {

				long userid = liferayUser.getUserId();
				long portletGroupId = dis.getScopeGroupId();
				long companyid = company.getCompanyId();
				String webid = company.getWebId();
				String login = liferayUser.getLogin();
				String email = liferayUser.getEmailAddress();
				Set<UserRole> roles = new HashSet<UserRole>();
				boolean admin = false;

				for (Role r : liferayUser.getRoles()) {
					boolean adminRole = "administrator".equalsIgnoreCase(r.getName());
					UserRole ur = new UserRole(r.getName(), r.getRoleId(), adminRole);
					roles.add(ur);
					admin |= adminRole;
				}
				// Group Roles
				for (UserGroupRole gr : UserGroupRoleLocalServiceUtil.getUserGroupRoles(liferayUser.getUserId())) {
					UserRole ur = new UserRole(gr.getRole().getName(), gr.getRoleId(), false);
					roles.add(ur);
				}

				for(Role role : this.getInheritedRoles(liferayUser)) {
					UserRole ur = new UserRole(role.getName(), role.getRoleId(), false);
					roles.add(ur);
				}
				
				
				Map<String, Object> userContext = new HashMap<String, Object>();
				userContext.put("p_auth", AuthTokenUtil.getToken(PortalUtil.getHttpServletRequest(request)));
				userContext.put("serverUri",
						"http://" + dis.getServerName() + ":" + dis.getServerPort() + "/api/jsonws/");
				user = new User(login, roles, admin, email, userid, portletGroupId, companyid, webid, userContext);
			}
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"Fehler beim Initialiseren des Users", "Fehler beim Initialiseren des Users"));
		}
	}

	/**
	 * Liefert alle vererbten Rollen des übergebenen Users zurück
	 * 
	 * @param user
	 * @return Roles
	 * @throws SystemException
	 * @throws PortalException
	 */
	private Set<Role> getInheritedRoles(com.liferay.portal.model.User user ) throws SystemException, PortalException {

		
		Set<Role> roles = new HashSet<>();
		
		List<Group> allGroups = new ArrayList<>();
		
		List<UserGroup> userGroups = user.getUserGroups();
		
		allGroups.addAll(GroupLocalServiceUtil.getUserGroupsGroups(userGroups));
		allGroups.addAll(GroupLocalServiceUtil.	getUserGroupsRelatedGroups(userGroups));

		List<Group> groups = user.getGroups();
		allGroups.addAll(groups);

		List<Organization> organizations = user.getOrganizations();
		allGroups.addAll(GroupLocalServiceUtil.getOrganizationsGroups(organizations));
		allGroups.addAll(GroupLocalServiceUtil.getOrganizationsRelatedGroups(organizations));
		
		for(Group group : allGroups){
			for(Role role : RoleLocalServiceUtil.getGroupRoles(group.getGroupId())) {
					roles.add(role);
			}
		}

		// 2. SITE-ROLLEN ÜBER BENUTZERGRUPPEN FINDEN
		for (UserGroup ug : user.getUserGroups()) {
		    // Wir holen alle Sites, in denen diese Benutzergruppe Mitglied ist
		    List<Group> groupsOfUserGroup = GroupLocalServiceUtil.getUserGroupGroups(ug.getUserGroupId());
		    
		    for (Group site : groupsOfUserGroup) {
		        // Jetzt fragen wir nach den Rollen, die die BENUTZERGRUPPE in dieser SITE hat
		        List<UserGroupGroupRole> uggrs = UserGroupGroupRoleLocalServiceUtil.getUserGroupGroupRoles(ug.getUserGroupId(), site.getGroupId());
		        
		        for (UserGroupGroupRole uggr : uggrs) {
		            Role r = uggr.getRole();
		            roles.add(r);
		        }
		    }
		}		
		
		
		return roles;

	}
	/*
	 * Liefert die im Report (.jrxml) definierten Parameter zurück
	 */
	public List<ReportParameter> getAperteReportParameters(ReportTemplate template) {
		return getReportMaster(template).getParameters();
	}

	/*
	 * Liefert den im Report (.jrxml) definierten Parameter mit dem übergebenen Key
	 * zurück
	 */
	private ReportParameter getAperteReportParameter(ReportTemplate template, String key) {
		for (ReportParameter param : getAperteReportParameters(template)) {
			if (param.getName().equals(key)) {
				return param;
			}
		}

		return null;
	}

	/*
	 * Initialisiert den ReportMaster und speichert ihn zwischen, wenn das Toggle
	 * geöffnet wird. Anschließend werden die Parameter für den Report
	 * initialisiert.
	 */
	public void initReportMaster(ReportTemplate template) {
		try {
			ReportMaster rm = new ReportMaster(template.getContent(), template.getId().toString(),
					new ReportTemplateProvider(), user);
			reportMasters.put(template.getId(), rm);

			Map<String, Object> params = new LinkedHashMap<String, Object>();
			for (ReportParameter parameter : getAperteReportParameters(template)) {
				params.put(parameter.getName(), parameter.getValue());
			}

			params.put(REPORT_TYPE_KEY, TYP_PDF);

			reportParameters.put(template.getId(), params);
		} catch (ARException e) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"Fehler beim Initialisieren der Parameter", "Fehler beim Initialisieren der Parameter"));
		}
	}

	/*
	 * Schmeißt den zwischengespeicherten ReportMaster und die Parameter wieder weg,
	 * wenn das Toggle geschlossen wird
	 */
	public void destroyReportMaster(ReportTemplate template) {
		reportMasters.remove(template.getId());
		reportParameters.remove(template.getId());
	}

	/*
	 * Setzt den SQL für MultiSelect-Parameter ab und gibt das Dictionary als Liste
	 * zurück
	 */
	public List<DictionaryItem> getDictionaryItems(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);

		if (parameter == null) {
			return new ArrayList<DictionaryItem>();
		}

		String dictQuery = parameter.getProperties().get(ARConstants.Keys.DICT_QUERY).getValue();
		if (StringUtils.isNotEmpty(dictQuery)) {
			String login = user.getLogin();
			dictQuery = dictQuery.replaceAll("\\$LOGIN", login);
			dictQuery = dictQuery.replaceAll(":webid", user.getWebid());

			return DictionaryDAO.fetchDictionary(dictQuery);
		}

		return new ArrayList<DictionaryItem>();
	}

	/*
	 * Liefert eine Liste aus SelectItems, die aus dem Dictionary erstellt wird
	 */
	public List<SelectItem> getDictionarySelectItems(ReportTemplate template, String key) {
		List<DictionaryItem> dictItems = getDictionaryItems(template, key);
		List<SelectItem> selectItems = new ArrayList<SelectItem>();
		for (DictionaryItem item : dictItems) {
			if (!StringUtils.isEmpty(item.getDescription())) {
				selectItems.add(new SelectItem(item.getCode(), item.getDescription()));
			}
		}

		return selectItems;
	}

	/*
	 * Generiert den Report und bietet ihn direkt zum Download an (XLS) bzw. öffnet
	 * ihn in einem neuen Tab (PDF)
	 */
	public void generateReport(ReportTemplate template) {
		byte[] reportBytes;
		String selectedType = getReportParameters(template).get(REPORT_TYPE_KEY).toString();
		
		try {
			Map<String, Object> params = collectParamsForExport(template);
			if(params == null) {
				return;
			}
			
			reportBytes = reportMasters.get(template.getId()).generateAndExportReport(selectedType, params,
					ConfigurationCache.getConfiguration());
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
					"Fehler beim Erstellen des Reports", "Fehler beim Erstellen des Reports"));
			return;
		}

		FacesContext facesContext = FacesContext.getCurrentInstance();
		ExternalContext externalContext = facesContext.getExternalContext();
		PortletResponse portletResponse = (PortletResponse) externalContext.getResponse();
		HttpServletResponse response = PortalUtil.getHttpServletResponse(portletResponse);
		ServletOutputStream output = null;

		try {
			response.reset();
			response.flushBuffer();
			portletResponse.setProperty("Content-Type", getContentType(template));
			portletResponse.setProperty("Content-Disposition",
					"attachment; filename=" + template.getReportname() + "." + selectedType.toLowerCase());

			output = response.getOutputStream();
			output.write(reportBytes);

			// Finalize task.
			output.flush();
			output.close();
			facesContext.responseComplete();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Sammelt die Parameter für den Export des Reports, reformatiert sie teilweise
	 * (multiselect) und reichert sie an
	 */
	private Map<String, Object> collectParamsForExport(ReportTemplate template) {
		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.putAll(getReportParameters(template));

		if (!checkRequiredParameters(template, params)) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_ERROR,
							"Bitte geben Sie alle mit * gekennzeichneten Pflichtfelder ein",
							"Bitte geben Sie alle mit * gekennzeichneten Pflichtfelder ein"));

			return null;
		}

		reformatMultiSelectParameters(template, params);
		reformatSingleSelectParameters(template, params);
		reformatDateParameters(template, params);

		params.put("login", user.getLogin());
		params.put("webid", user.getWebid());

		return params;
	}

	private void reformatDateParameters(ReportTemplate template, Map<String, Object> params) {
		for (Entry<String, Object> entry : params.entrySet()) {
			if (isDateInput(template, entry.getKey())) {
				try {
					Date date = new SimpleDateFormat("yyyy-MM-dd").parse(entry.getValue().toString());
					params.put(entry.getKey(), new SimpleDateFormat("dd-MM-yyyy HH:mm").format(date));
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * Reformatiert die MultiSelect Parameter. Die SelectManyList speichert die
	 * selektierten Elemente als String-Array Aperte braucht die Parameter
	 * allerdings als Komma-separierte Strings Wenn an der Oberfläche kein Element
	 * ausgewählt ist, wird ein Leerstring Übergeben
	 */
	private void reformatMultiSelectParameters(ReportTemplate template, Map<String, Object> params) {
		for (Entry<String, Object> entry : params.entrySet()) {
			if (isMultiSelectInput(template, entry.getKey())) {
				Object value = params.get(entry.getKey());
				if (value instanceof String[]) {
					String[] array = (String[]) value;

					if (array.length == 0) {
						params.put(entry.getKey(), "");
					} else {
						String newValue = "";
						for (String each : array) {
							newValue += "'" + each + "',";
						}

						params.put(entry.getKey(), newValue.substring(0, newValue.lastIndexOf(",")));
					}
				}
			}
		}
	}

	private void reformatSingleSelectParameters(ReportTemplate template, Map<String, Object> params) {
		Map<String, Object> paramsCopy = new HashMap<String, Object>();
		paramsCopy.putAll(params);
		for (Entry<String, Object> entry : params.entrySet()) {
			if (isSingleSelectInput(template, entry.getKey()) && entry.getValue() == null) {
				paramsCopy.remove(entry.getKey());
			}
		}

		params.clear();
		params.putAll(paramsCopy);
	}

	/*
	 * Prüft, ob alle Pflichtfelder gefüllt sind
	 */
	private boolean checkRequiredParameters(ReportTemplate template, Map<String, Object> params) {
		for (Entry<String, Object> entry : params.entrySet()) {
			if (isParameterRequired(template, entry.getKey())
					&& (entry.getValue() == null || StringUtils.isEmpty(entry.getValue().toString()))) {
				return false;
			}
		}

		return true;
	}

	public boolean isParameterRequired(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);
		if (parameter == null) {
			return false;
		}
		ReportProperty requiredProperty = parameter.getProperties().get(ARConstants.Keys.REQUIRED);
		return requiredProperty != null && "true".equals(requiredProperty.getValue());
	}

	public boolean isTextInput(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);
		return parameter != null && parameter.getProperties().get(ARConstants.Keys.INPUT_TYPE).getValue()
				.equalsIgnoreCase(ARConstants.InputTypes.TEXT.toString());
	}

	public boolean isDateInput(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);
		return parameter != null && parameter.getProperties().get(ARConstants.Keys.INPUT_TYPE).getValue()
				.equalsIgnoreCase(ARConstants.InputTypes.DATE.toString());
	}

	public boolean isMultiSelectInput(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);
		if (parameter == null) {
			return false;
		}
		String inputType = parameter.getProperties().get(ARConstants.Keys.INPUT_TYPE).getValue();
		return inputType.equalsIgnoreCase(ARConstants.InputTypes.MULTISELECT.toString());
	}

	public boolean isSingleSelectInput(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);
		if (parameter == null) {
			return false;
		}
		String inputType = parameter.getProperties().get(ARConstants.Keys.INPUT_TYPE).getValue();
		return inputType.equalsIgnoreCase(ARConstants.InputTypes.SELECT.toString());
	}

	public ReportMaster getReportMaster(ReportTemplate template) {
		return reportMasters.get(template.getId());
	}

	public String getParameterLabel(ReportTemplate template, String key) {
		ReportParameter parameter = getAperteReportParameter(template, key);
		if (parameter == null) {
			return "";
		}
		String label = parameter.getProperties().get(ARConstants.Keys.LABEL).getValue(); 
		if(this.isParameterRequired(template, key))
			label = label + "*";
		return label;
	}

	public List<ReportTemplate> getReportTemplates() {
		return reportTemplates;
	}

	public void setReportTemplates(List<ReportTemplate> reportTemplates) {
		this.reportTemplates = reportTemplates;
	}

	public Map<String, Object> getReportParameters(ReportTemplate template) {
		return reportParameters.get(template.getId());
	}

	public List<SelectItem> getReportTypeSelectItems() {
		List<SelectItem> typItems = new ArrayList<SelectItem>();
		typItems.add(new SelectItem(TYP_PDF, TYP_PDF));
		typItems.add(new SelectItem(TYP_XLS, TYP_XLS));

		return typItems;
	}

	private String getContentType(ReportTemplate template) {
		String selectedType = getReportParameters(template).get(REPORT_TYPE_KEY).toString();
		if (TYP_PDF.equals(selectedType)) {
			return "application/pdf";
		} else {
			return "application/vnd.ms-excel";
		}
	}

}
