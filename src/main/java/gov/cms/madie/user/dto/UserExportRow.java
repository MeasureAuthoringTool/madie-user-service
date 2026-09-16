package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single row of the Full User Export. Field order/names mirror the excel-export service's
 * UserExportRowDto exactly (33 columns). All fields are optional strings.
 *
 * <p>For this story rows are not populated; see {@code UserExportService#buildRows()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserExportRow {

  // --- User Metadata (columns 1-9) ---
  private String userDisplayName;
  private String firstName;
  private String lastName;
  private String harpId;
  private String emailAddress;
  private String userStatus;
  private String roles;
  private String approval;
  private String lastLogin;

  // --- User's Owned Measures (columns 10-15) ---
  private String ownedMeasureName;
  private String ownedMeasureVersion;
  private String ownedMeasureStatus;
  private String ownedMeasureModel;
  private String ownedMeasureCmsId;
  private String ownedMeasureUpdated;

  // --- Measures Shared with User (columns 16-22) ---
  private String sharedMeasureName;
  private String sharedMeasureVersion;
  private String sharedMeasureStatus;
  private String sharedMeasureModel;
  private String sharedMeasureCmsId;
  private String sharedMeasureOwner;
  private String sharedMeasureUpdated;

  // --- User's Owned Libraries (columns 23-27) ---
  private String ownedLibraryName;
  private String ownedLibraryVersion;
  private String ownedLibraryStatus;
  private String ownedLibraryModel;
  private String ownedLibraryUpdated;

  // --- Libraries Shared with User (columns 28-33) ---
  private String sharedLibraryName;
  private String sharedLibraryVersion;
  private String sharedLibraryStatus;
  private String sharedLibraryModel;
  private String sharedLibraryOwner;
  private String sharedLibraryUpdated;
}
