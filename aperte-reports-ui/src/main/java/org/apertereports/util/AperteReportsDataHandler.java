package org.apertereports.util;

import com.liferay.portal.kernel.lar.BasePortletDataHandler;
import com.liferay.portal.kernel.lar.PortletDataHandlerBoolean;
import com.liferay.portal.kernel.lar.PortletDataHandlerControl;
import javax.portlet.PortletPreferences;

public class AperteReportsDataHandler extends BasePortletDataHandler {
	
	@Override
	public PortletDataHandlerControl[] getExportControls() {
		PortletDataHandlerBoolean test1 = new PortletDataHandlerBoolean("test1", "Test1", true, false);
		PortletDataHandlerBoolean test2 = new PortletDataHandlerBoolean("test2", "Test2", true, false);
		return new PortletDataHandlerControl[] { test1, test2 };
	}

	@Override
	public PortletDataHandlerControl[] getImportControls() {
		return getExportControls();
	}
}
