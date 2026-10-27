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
import java.util.Collections;

import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.queries.QueryObjectProvider;
import org.bimserver.database.queries.om.Query;
import org.bimserver.database.queries.om.QueryException;
import org.bimserver.database.queries.om.QueryPart;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.emf.Schema;
import org.bimserver.shared.HashMapVirtualObject;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;

public class ChangeHelper {
	public static boolean canBeChanged(EClass eClass) {
		return Schema.isIfc(eClass.getEPackage());
	}

	/**
	 * The object as changed so far in this transaction, or else as it is in the previous revision. Null when it does not exist or was deleted in this transaction.
	 */
	public static HashMapVirtualObject getObject(Transaction transaction, PackageMetaData packageMetaData, long oid) throws IOException, QueryException, BimserverDatabaseException {
		if (transaction.isDeleted(oid)) {
			return null;
		}
		HashMapVirtualObject object = transaction.get(oid);
		if (object == null) {
			Query query = new Query(packageMetaData);
			QueryPart queryPart = query.createQueryPart();
			queryPart.addOid(oid);

			QueryObjectProvider queryObjectProvider = new QueryObjectProvider(transaction.getDatabaseSession(), transaction.getBimServer(), query, Collections.singleton(transaction.getPreviousRevision().getOid()), packageMetaData);
			object = queryObjectProvider.next();
		}
		return object;
	}

	/**
	 * Removes the back-reference to oid that otherOid holds in the inverse or opposite of eReference, if there is one.
	 */
	public static void removeOpposite(Transaction transaction, PackageMetaData packageMetaData, EReference eReference, long otherOid, long oid) throws IOException, QueryException, BimserverDatabaseException {
		EClass otherEClass = transaction.getDatabaseSession().getEClassForOid(otherOid);
		EReference opposite = packageMetaData.getInverseOrOpposite(otherEClass, eReference);
		if (opposite == null) {
			return;
		}
		HashMapVirtualObject other = getObject(transaction, packageMetaData, otherOid);
		if (other == null) {
			return;
		}
		if (opposite.isMany()) {
			other.removeReference(opposite, oid);
		} else if (Long.valueOf(oid).equals(other.eGet(opposite))) {
			other.eUnset(opposite);
		} else {
			return;
		}
		transaction.updated(other);
	}
}
