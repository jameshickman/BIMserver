package org.bimserver.tests.serviceinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.bimserver.interfaces.objects.SProject;
import org.bimserver.interfaces.objects.SUser;
import org.bimserver.interfaces.objects.SUserType;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.tests.TestWithEmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ServiceInterface.getProjectByUuid against a running server, see issue #1353.
 */
public class TestGetProjectByUuid extends TestWithEmbeddedServer {

	private BimServerClientInterface admin;
	private SProject project;

	@BeforeEach
	public void createProject() throws Exception {
		admin = getFactory().create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));
		project = admin.getServiceInterface().addProject("by-uuid-" + System.nanoTime(), "ifc4");
	}

	@AfterEach
	public void disconnect() throws Exception {
		admin.close();
	}

	@Test
	public void findsProjectByItsUuid() throws Exception {
		SProject found = admin.getServiceInterface().getProjectByUuid(project.getUuid().toString());

		assertThat(found.getOid()).isEqualTo(project.getOid());
		assertThat(found.getName()).isEqualTo(project.getName());
	}

	@Test
	public void rejectsUnknownUuid() {
		assertThatThrownBy(() -> admin.getServiceInterface().getProjectByUuid(UUID.randomUUID().toString()))
				.isInstanceOf(UserException.class)
				.hasMessageContaining("does not exist");
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "" })
	public void rejectsMalformedUuid(String uuid) {
		assertThatThrownBy(() -> admin.getServiceInterface().getProjectByUuid(uuid))
				.isInstanceOf(UserException.class)
				.hasMessageContaining("Invalid uuid");
	}

	@Test
	public void onlyAuthorizedUsersCanFindProject() throws Exception {
		String username = "uuid-" + System.nanoTime() + "@bimserver.org";
		SUser user = admin.getServiceInterface().addUserWithPassword(username, "password", "Uuid Tester", SUserType.USER, false, null);

		try (BimServerClientInterface client = getFactory().create(new UsernamePasswordAuthenticationInfo(username, "password"))) {
			assertThatThrownBy(() -> client.getServiceInterface().getProjectByUuid(project.getUuid().toString()))
					.isInstanceOf(UserException.class)
					.hasMessageContaining("do not have rights");

			admin.getServiceInterface().addUserToProject(user.getOid(), project.getOid());

			assertThat(client.getServiceInterface().getProjectByUuid(project.getUuid().toString()).getOid()).isEqualTo(project.getOid());
		}
	}
}
