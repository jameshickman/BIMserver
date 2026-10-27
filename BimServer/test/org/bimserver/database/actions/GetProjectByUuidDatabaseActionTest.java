package org.bimserver.database.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bimserver.database.DatabaseSession;
import org.bimserver.database.query.conditions.Condition;
import org.bimserver.emf.IdEObjectImpl;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.models.store.ObjectState;
import org.bimserver.models.store.Project;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.models.store.StorePackage;
import org.bimserver.models.store.User;
import org.bimserver.models.store.UserType;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.webservices.authorization.Authorization;
import org.bimserver.webservices.authorization.UserAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lookup of a project by its object UUID, see issue #1353.
 */
@ExtendWith(MockitoExtension.class)
class GetProjectByUuidDatabaseActionTest {

	private static final long USER_OID = 42L;

	@Mock
	private DatabaseSession databaseSession;

	private final Map<UUID, Project> projectsByUuid = new HashMap<>();
	private final Authorization authorization = new UserAuthorization(1, TimeUnit.HOURS);
	private User user;
	private Project project;

	@BeforeEach
	void setUp() throws Exception {
		authorization.setUoid(USER_OID);
		user = newUser(UserType.USER);
		project = newProject(ObjectState.ACTIVE);
		project.getHasAuthorizedUsers().add(user);

		lenient().when(databaseSession.queryUuid(any(Condition.class), eq(Project.class), any())).thenReturn(projectsByUuid);
		lenient().when(databaseSession.get(eq(StorePackage.eINSTANCE.getUser()), anyLong(), any())).thenAnswer(invocation -> user);
	}

	@Nested
	@DisplayName("returns the project")
	class Found {

		@Test
		@DisplayName("to a user who is authorized on it")
		void authorizedUser() throws Exception {
			assertThat(execute(project.getUuid().toString())).isSameAs(project);
		}

		@ParameterizedTest
		@EnumSource(value = UserType.class, names = { "ADMIN", "SYSTEM" })
		@DisplayName("to admins and the system even when not explicitly authorized")
		void privilegedUser(UserType userType) throws Exception {
			user = newUser(userType);

			assertThat(execute(project.getUuid().toString())).isSameAs(project);
		}

		@ParameterizedTest
		@EnumSource(value = ObjectState.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
		@DisplayName("to admins even when it is not active")
		void inactiveProjectForAdmin(ObjectState state) throws Exception {
			project.setState(state);
			user = newUser(UserType.ADMIN);

			assertThat(execute(project.getUuid().toString())).isSameAs(project);
		}

		@Test
		@DisplayName("when the UUID is given in upper case")
		void upperCaseUuid() throws Exception {
			assertThat(execute(project.getUuid().toString().toUpperCase())).isSameAs(project);
		}
	}

	@Nested
	@DisplayName("refuses")
	class Refused {

		@Test
		@DisplayName("a UUID that matches no project")
		void unknownUuid() {
			assertThatThrownBy(() -> execute(UUID.randomUUID().toString()))
					.isInstanceOf(UserException.class)
					.hasMessageContaining("does not exist");
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "not-a-uuid", "1234", "  " })
		@DisplayName("a malformed UUID")
		void malformedUuid(String uuid) {
			assertThatThrownBy(() -> execute(uuid))
					.isInstanceOf(UserException.class)
					.hasMessageContaining("Invalid uuid");
		}

		@Test
		@DisplayName("a user without rights on the project")
		void unauthorizedUser() {
			project.getHasAuthorizedUsers().clear();

			assertThatThrownBy(() -> execute(project.getUuid().toString()))
					.isInstanceOf(UserException.class)
					.hasMessageContaining("do not have rights");
		}

		@ParameterizedTest
		@EnumSource(value = ObjectState.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
		@DisplayName("a regular user when the project is not active")
		void inactiveProject(ObjectState state) {
			project.setState(state);

			assertThatThrownBy(() -> execute(project.getUuid().toString()))
					.isInstanceOf(UserException.class)
					.hasMessageContaining("not active");
		}

		@Test
		@DisplayName("a request without an authenticated user")
		void noUser() {
			user = null;

			assertThatThrownBy(() -> execute(project.getUuid().toString()))
					.isInstanceOf(UserException.class)
					.hasMessageContaining("Authenticated user required");
		}
	}

	private Project execute(String uuid) throws Exception {
		return new GetProjectByUuidDatabaseAction(databaseSession, AccessMethod.INTERNAL, uuid, authorization).execute();
	}

	private Project newProject(ObjectState state) {
		Project newProject = StoreFactory.eINSTANCE.createProject();
		((IdEObjectImpl) newProject).setUuid(UUID.randomUUID());
		newProject.setState(state);
		projectsByUuid.put(newProject.getUuid(), newProject);
		return newProject;
	}

	private static User newUser(UserType userType) {
		User newUser = StoreFactory.eINSTANCE.createUser();
		((IdEObjectImpl) newUser).setOid(USER_OID);
		newUser.setUserType(userType);
		return newUser;
	}
}
