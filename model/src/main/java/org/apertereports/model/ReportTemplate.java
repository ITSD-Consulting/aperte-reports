package org.apertereports.model;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.*;
import org.hibernate.annotations.Type;

/**
 * Holds all the Jasper report template data and specific report configuration.
 */
@Entity
@Table(name = "ar_report_template", uniqueConstraints =
@UniqueConstraint(columnNames = {"reportname", "companyId"}))
public class ReportTemplate implements Serializable {

    private static final long serialVersionUID = -7196776812526154079L;
    /**
     * Id indicating access for all roles to the report
     */
    public static final long ACCESS_ALL_ROLES_ID = -1;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ar_roles_with_access", joinColumns =
    @JoinColumn(name = "report_id"))
    @Column(name = "role_id")
    private Set<Long> rolesWithAccess = new HashSet<Long>();
    /**
     * Indicates this report is active or not.
     */
    @Column
    private Boolean active = true;
    /**
     * JRXML data formatted in Base64 manner.
     */
    @Lob
    @Type(type = "org.hibernate.type.StringClobType")
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String content;
    /**
     * Date of creation.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", updatable = false)
    private Date created = new Date();
    /**
     * Description of the report template.
     */
    @Column
    private String description;
    /**
     * Filename it was uploaded from.
     */
    @Column(nullable = false)
    private String filename;

    @Column(nullable = true, name="companyId")
    private String companyId;

    public String getCompanyId() {
		return companyId;
	}

	public void setCompanyId(String companyId) {
		this.companyId = companyId;
	}

	/**
     * Should report engine allow online display.
     */
    @Column(name = "allow_online_display")
    private Boolean allowOnlineDisplay = true;
    /**
     * Should report engine allow background order generation.
     */
    @Column(name = "allow_background_order")
    private Boolean allowBackgroundOrder = true;
    /**
     * Primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @PrimaryKeyJoinColumn
    @Column(name = "id", nullable = false, length = 10)
    private Integer id;
    /**
     * Report name taken from JRXML it was uploaded from (the
     * <code>name</code> attribute of
     * <code>jasperReport</code> tag).
     */
    @Column(nullable = false)
    private String reportname;

    public boolean getActive() {
        return active != null && active;
    }

    public String getContent() {
        return content;
    }

    public Date getCreated() {
        return created;
    }

    public String getDescription() {
        return description;
    }

    public String getFilename() {
        return filename;
    }

    public Integer getId() {
        return id;
    }

    public String getReportname() {
        return reportname;
    }

    public Boolean getAllowBackgroundOrder() {
        return allowBackgroundOrder != null && allowBackgroundOrder;
    }

    public Boolean getAllowOnlineDisplay() {
        return allowOnlineDisplay != null && allowOnlineDisplay;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setAllowBackgroundOrder(Boolean allowBackgroundOrder) {
        this.allowBackgroundOrder = allowBackgroundOrder;
    }

    public void setAllowOnlineDisplay(Boolean allowOnlineDisplay) {
        this.allowOnlineDisplay = allowOnlineDisplay;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setReportname(String reportname) {
        this.reportname = reportname;
    }

    /**
     * Determines if the report is accessible for all roles
     *
     * @return true if the report is accessible for all roles, false otherwise
     */
    public boolean isAccessibleForAllRoles() {
        return rolesWithAccess.size() == 1 && rolesWithAccess.contains(ACCESS_ALL_ROLES_ID);
    }

    /**
     * Returns the set of role ids with access to the report. The set can be
     * ampty when none role has access or all roles have access. First, the
     * method
     * {@link #isAccessibleForAllRoles()} should be used.
     *
     * @return List of role ids
     * @see #isAccessibleForAllRoles()
     */
    public Set<Long> getRolesWithAccess() {
        return rolesWithAccess;
    }

    /**
     * Sets the report accessible for all roles in the application
     */
    public void setAccessibleForAllRoles() {
        rolesWithAccess.clear();
        rolesWithAccess.add(ACCESS_ALL_ROLES_ID);
    }

    /**
     * Sets the access to the report for roles with given ids. If none role has
     * to have the access, null value should be passed or empty set. To set the
     * access for all roles use {@link #setAccessibleForAllRoles()}
     *
     * @param ids List of roles ids. If the list is empty or null value is
     * passed, none role will have the access
     * @see #setAccessibleForAllRoles()
     */
    public void setRolesWithAccess(Set<Long> rolesWithAccess) {
        this.rolesWithAccess = rolesWithAccess;
    }

    /**
     * Determines if the role with given id has the access to the report
     *
     * @param roleId Role id
     * @return true if the role has access, false otherwise
     */
    public boolean hasRoleAccess(Long roleId) {
        return rolesWithAccess.contains(ACCESS_ALL_ROLES_ID) || rolesWithAccess.contains(roleId);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((reportname == null) ? 0 : reportname.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ReportTemplate other = (ReportTemplate) obj;
        if (reportname == null) {
            if (other.reportname != null) {
                return false;
            }
        } else if (!reportname.equals(other.reportname)) {
            return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + ", id: " + id;
    }
}
