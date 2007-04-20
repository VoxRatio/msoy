//
// $Id$

package com.threerings.msoy.item.server.persist;

import com.samskivert.jdbc.depot.Key;
import com.samskivert.jdbc.depot.annotation.Entity;
import com.samskivert.jdbc.depot.annotation.Table;
import com.samskivert.jdbc.depot.annotation.TableGenerator;

/** Clone records for Audios. */
@Entity
@Table
@TableGenerator(name="cloneId", allocationSize=-1,
                initialValue=-1, pkColumnValue="AUDIO_CLONE")
public class AudioCloneRecord extends CloneRecord<AudioRecord>
{

    // AUTO-GENERATED: METHODS START
    /**
     * Create and return a primary {@link Key} to identify a {@link #AudioCloneRecord}
     * with the supplied key values.
     */
    public static Key<AudioCloneRecord> getKey (int itemId)
    {
        return new Key<AudioCloneRecord>(
                AudioCloneRecord.class,
                new String[] { ITEM_ID },
                new Comparable[] { itemId });
    }
    // AUTO-GENERATED: METHODS END
}
