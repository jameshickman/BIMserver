package org.bimserver.tests.ifc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SActionState;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SLongCheckinActionState;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.ifc4.IfcArcIndex;
import org.bimserver.models.ifc4.IfcIndexedPolyCurve;
import org.bimserver.models.ifc4.IfcLabel;
import org.bimserver.models.ifc4.IfcLineIndex;
import org.bimserver.models.ifc4.IfcPropertyListValue;
import org.bimserver.models.ifc4.IfcSegmentIndexSelect;
import org.bimserver.models.ifc4.IfcValue;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.tests.TestWithEmbeddedServer;
import org.junit.jupiter.api.Test;

/**
 * Check-in and read-back of wrapped values that are themselves lists, see issue #1268.
 */
public class TestStyledIndexedPolyCurve extends TestWithEmbeddedServer {

	@Test
	public void segmentsAndPropertyListValuesSurviveCheckin() throws Exception {
		try (BimServerClientInterface client = getFactory().create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
			SProject project = client.getServiceInterface().addProject("polycurve-" + System.nanoTime(), "ifc4");
			SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
			Path file = Paths.get(getClass().getResource("styled-indexed-polycurve.ifc").toURI());

			SLongCheckinActionState checkin = client.checkinSync(project.getOid(), "styled polycurve", deserializer.getOid(), false, file);

			assertThat(checkin.getState()).isEqualTo(SActionState.FINISHED);
			assertThat(checkin.getErrors()).isEmpty();

			project = client.getServiceInterface().getProjectByPoid(project.getOid());
			IfcModelInterface model = client.getModel(project, project.getLastRevisionId(), true, false);

			List<IfcIndexedPolyCurve> curves = model.getAll(IfcIndexedPolyCurve.class);
			assertThat(curves).hasSize(1);
			List<IfcSegmentIndexSelect> segments = curves.get(0).getSegments();
			assertThat(segments).hasSize(3);
			assertThat(segments.get(0)).isInstanceOfSatisfying(IfcLineIndex.class, s -> assertThat(s.getWrappedValue()).containsExactly(1L, 2L));
			assertThat(segments.get(1)).isInstanceOfSatisfying(IfcArcIndex.class, s -> assertThat(s.getWrappedValue()).containsExactly(2L, 3L, 4L));
			assertThat(segments.get(2)).isInstanceOfSatisfying(IfcLineIndex.class, s -> assertThat(s.getWrappedValue()).containsExactly(4L, 5L, 1L));

			List<IfcPropertyListValue> properties = model.getAll(IfcPropertyListValue.class);
			assertThat(properties).hasSize(1);
			List<IfcValue> values = properties.get(0).getListValues();
			assertThat(values)
					.allSatisfy(value -> assertThat(value).isInstanceOf(IfcLabel.class))
					.extracting(value -> ((IfcLabel) value).getWrappedValue())
					.containsExactly("first", "second");
		}
	}
}
