# Madie User Service

A Spring Boot microservice for managing user data and authentication for the Madie platform. This service provides RESTful APIs for user details, activity tracking, and integrates with MongoDB for persistence.

## Features
- User CRUD operations
- Activity tracking
- MongoDB index configuration
- CORS support for multiple environments
- Security configuration

## Technologies Used
- Java 17
- Spring Boot
- Spring Data MongoDB
- Mockito & Hamcrest (unit testing)
- JUnit 5
- Maven

## Prerequisites
- Java 17+
- Maven
- MongoDB (local or remote)

## Setup Instructions
1. **Clone the repository:**
   ```sh
   git clone https://github.com/MeasureAuthoringTool/madie-user-service.git
   cd madie-user-service
   ```
2. **Configure MongoDB:**
   - Update `src/main/resources/application.yaml` with your MongoDB connection details.
3. **Run the application:**
   ```sh
   ./mvnw spring-boot:run
   ```
   The service will start on `http://localhost:8088` by default.

## Running Tests
- **Unit and Integration Tests:**
  ```sh
  ./mvnw test
  ```
- Unit tests use Mockito and Hamcrest for mocking and assertions.
- Integration tests use SpringBootTest to verify context and bean loading.

## API Endpoints
See the controller classes in `src/main/java/gov/cms/madie/user/controllers/` for available endpoints and request/response models.

## CI/CD & Security
- GitHub Actions workflow runs Gitleaks to scan for secrets on every push.
- Slack notifications are sent on scan failures.

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License
This project is licensed under the MIT License.

## Contact
For questions or support, please contact the Madie team or open an issue in the repository.

