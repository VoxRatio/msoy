//
// $Id$

package com.threerings.msoy.item.server.persist;

import com.samskivert.jdbc.depot.Key;
import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.Table;

/** Rating records for Pet. */
@Entity
@Table
public class PetRatingRecord extends RatingRecord<PetRecord>
{

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link #PetRatingRecord}
     * with the supplied key values.
     */
    public static Key<PetRatingRecord> getKey (int itemId, int memberId)
    {
        return new Key<PetRatingRecord>(
                PetRatingRecord.class,
                new String[] { ITEM_ID, MEMBER_ID },
                new Comparable[] { itemId, memberId });
    }
    // AUTO-GENERATED: METHODS END
}
