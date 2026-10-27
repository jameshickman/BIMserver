package org.bimserver.client.tests;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.ClientIfcModel;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SLongCheckinActionState;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.ifc4.IfcArcIndex;
import org.bimserver.models.ifc4.IfcIndexedPolyCurve;
import org.bimserver.models.ifc4.IfcLineIndex;
import org.bimserver.models.ifc4.IfcSegmentIndexSelect;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.google.common.base.Joiner;

@Tag("live-server")
public class TestIfc4TwoDimensional3 {
	@Test
	public void test() throws Exception {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				SProject project = client.getServiceInterface().addProject(RandomStringUtils.randomAlphanumeric(10), "ifc4");
				SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
				Path path = Paths.get(System.getProperty("testFileLocation"), "TestData/data/ifc4add2tc1/slab-standard-case.ifc");
				SLongCheckinActionState actionState = client.checkinSync(project.getOid(), "test", deserializer.getOid(), path, (title, progress) ->
						System.out.println(title + ": " + progress)
				);
				ClientIfcModel model = client.getModel(project, actionState.getRoid(), true, false);

				List<IfcIndexedPolyCurve> polyCurves = model.getAll(IfcIndexedPolyCurve.class);
				Assertions.assertEquals(1, polyCurves.size());
				for (IfcIndexedPolyCurve ifcIndexedPolyCurve : polyCurves) {
					EList<IfcSegmentIndexSelect> segments = ifcIndexedPolyCurve.getSegments();
					Assertions.assertEquals(4, segments.size());
					for (IfcSegmentIndexSelect ifcSegmentIndexSelect : segments) {
						if (ifcSegmentIndexSelect instanceof IfcLineIndex) {
							Assertions.assertEquals(2, ((IfcLineIndex)ifcSegmentIndexSelect).getWrappedValue().size());
						} else if (ifcSegmentIndexSelect instanceof IfcArcIndex){
							Assertions.assertEquals(3, ((IfcArcIndex) ifcSegmentIndexSelect).getWrappedValue().size());
						}
					}
				}
			}
			Thread.sleep(500);
		}
	}
}
