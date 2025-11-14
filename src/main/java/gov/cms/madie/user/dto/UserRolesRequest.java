package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesRequest {
    private String userName;
    private String adoName;
    private String programName;
}

