//
// $Id$

package com.threerings.msoy.item.server.persist;

import com.samskivert.jdbc.depot.Key;
import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.Table;

/** Rating records for Documents. */
@Entity
@Table
public class DocumentRatingRecord extends RatingRecord<DocumentRecord>
{

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link #DocumentRatingRecord}
     * with the supplied key values.
     */
    public static Key<DocumentRatingRecord> getKey (int itemId, int memberId)
    {
        return new Key<DocumentRatingRecord>(
                DocumentRatingRecord.class,
                new String[] { ITEM_ID, MEMBER_ID },
                new Comparable[] { itemId, memberId });
    }
    // AUTO-GENERATED: METHODS END
}
