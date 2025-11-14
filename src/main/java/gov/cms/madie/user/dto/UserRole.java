package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {
    private String status;
    private String orgName;
    private String programName;
    private String displayName;
    private String roleType;
    private String roleValue;
    private String startDate;
    private boolean isSoRole;
    private String systemName;
}

