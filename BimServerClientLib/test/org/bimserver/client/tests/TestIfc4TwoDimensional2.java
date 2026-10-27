package org.bimserver.client.tests;

import org.apache.commons.lang3.RandomStringUtils;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.ClientIfcModel;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SLongCheckinActionState;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.ifc4.IfcBSplineCurveWithKnots;
import org.bimserver.models.ifc4.IfcBSplineSurfaceWithKnots;
import org.bimserver.models.ifc4.IfcCartesianPoint;
import org.bimserver.models.ifc4.ListOfIfcCartesianPoint;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Tag("live-server")
public class TestIfc4TwoDimensional2 {
	@Test
	public void test() throws Exception {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				SProject project = client.getServiceInterface().addProject(RandomStringUtils.randomAlphanumeric(10), "ifc4");
				SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
				Path path = Paths.get(System.getProperty("testFileLocation"), "TestData/data/ifc4add2tc1/basin-advanced-brep.ifc");
				SLongCheckinActionState actionState = client.checkinSync(project.getOid(), "test", deserializer.getOid(), path, (title, progress) ->
						System.out.println(title + ": " + progress)
				);
				ClientIfcModel model = client.getModel(project, actionState.getRoid(), true, false);
				List<IfcBSplineCurveWithKnots> bSplineCurves = model.getAll(IfcBSplineCurveWithKnots.class);
				Assertions.assertEquals(4, bSplineCurves.size());
				for (IfcBSplineCurveWithKnots bSplineCurve : bSplineCurves) {
					Assertions.assertEquals(7, bSplineCurve.getControlPointsList().size());
					for (org.bimserver.models.ifc4.IfcCartesianPoint point : bSplineCurve.getControlPointsList()) {
						Assertions.assertNotNull(point);
						Assertions.assertTrue(point.getCoordinates().size()==2 || point.getCoordinates().size()==3);
					}
				}
				List<IfcBSplineSurfaceWithKnots> bSplineSurfaces = model.getAll(IfcBSplineSurfaceWithKnots.class);
				Assertions.assertEquals(2, bSplineSurfaces.size());
				for (IfcBSplineSurfaceWithKnots bSplineSurface: bSplineSurfaces){
					Assertions.assertEquals(4, bSplineSurface.getControlPointsList().size());
					for(ListOfIfcCartesianPoint pointList: bSplineSurface.getControlPointsList()){
						Assertions.assertEquals(7, pointList.getList().size());
						for(IfcCartesianPoint point: pointList.getList()){
							Assertions.assertNotNull(point);
							Assertions.assertEquals(3, point.getCoordinates().size());
						}
					}
				}
			}
			Thread.sleep(500);
		}
	}
}
