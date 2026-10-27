package org.bimserver.changes;

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.io.IOException;

import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.BimserverLockConflictException;
import org.bimserver.database.queries.om.QueryException;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.shared.HashMapVirtualObject;
import org.bimserver.shared.exceptions.DefaultErrorCode;
import org.bimserver.shared.exceptions.UserException;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;

public class AddReferenceChange implements Change {

	private final long oid;
	private final String referenceName;
	private final long referenceOid;

	public AddReferenceChange(long oid, String referenceName, long referenceOid) {
		this.oid = oid;
		this.referenceName = referenceName;
		this.referenceOid = referenceOid;
	}

	@Override
	public void execute(Transaction transaction) throws UserException, BimserverLockConflictException, BimserverDatabaseException, IOException, QueryException {
		PackageMetaData packageMetaData = transaction.getDatabaseSession().getMetaDataManager().getPackageMetaData(transaction.getProject().getSchema());

		HashMapVirtualObject object = ChangeHelper.getObject(transaction, packageMetaData, oid);
		
		EClass eClass = transaction.getDatabaseSession().getEClassForOid(oid);
		if (!ChangeHelper.canBeChanged(eClass)) {
			throw new UserException("Only objects from the following schemas are allowed to be changed: Ifc2x3tc1 and IFC4, this object (" + eClass.getName() + ") is from the \"" + eClass.getEPackage().getName() + "\" package");
		}
		if (object == null) {
			throw new UserException("No object of type \"" + eClass.getName() + "\" with oid " + oid + " found in project with pid " + transaction.getProject().getId());
		}
		EReference eReference = packageMetaData.getEReference(eClass.getName(), referenceName);
		if (eReference == null) {
			throw new UserException("No reference with the name \"" + referenceName + "\" found in class \"" + eClass.getName() + "\"");
		}
		if (!eReference.isMany()) {
			throw new UserException("Reference is not of type 'many'");
		}
		
		EClass eClassForOid = transaction.getDatabaseSession().getEClassForOid(referenceOid);
		EReference inverseOrOpposite = packageMetaData.getInverseOrOpposite(eClassForOid, eReference);
		HashMapVirtualObject referencedObject = null;
		if (inverseOrOpposite != null) {
			referencedObject = ChangeHelper.getObject(transaction, packageMetaData, referenceOid);
			if (referencedObject == null) {
				throw new UserException("No object of type \"" + eClassForOid.getName() + "\" with oid " + referenceOid + " found in project with pid " + transaction.getProject().getId());
			}
			if (!inverseOrOpposite.isMany()) {
				Object existing = referencedObject.eGet(inverseOrOpposite);
				if (existing instanceof Long && (Long) existing != oid) {
					throw new UserException("Cannot add " + eClassForOid.getName() + " " + referenceOid + " to " + eClass.getName() + "." + referenceName + ", its " + inverseOrOpposite.getName() + " already refers to object " + existing, DefaultErrorCode.SET_REFERENCE_FAILED_OPPOSITE_ALREADY_SET);
				}
			}
		}

		object.addReference(eReference, eClassForOid, referenceOid);
		transaction.updated(object);

		if (referencedObject != null) {
			if (inverseOrOpposite.isMany()) {
				referencedObject.addReference(inverseOrOpposite, eClass, oid);
			} else {
				referencedObject.setReference(inverseOrOpposite, oid);
			}
			transaction.updated(referencedObject);
		}
	}
}