//
// $Id$

package com.threerings.msoy.notify.data;

import com.threerings.util.ActionScript;
import com.threerings.util.MessageBundle;

import com.threerings.orth.notify.data.Notification;

import com.threerings.msoy.data.all.MemberName;

/**
 * Notifies a user that they've been poked.
 */
public class PokeNotification extends Notification
{
    public PokeNotification ()
    {
    }

    @ActionScript(omit=true)
    public PokeNotification (MemberName poker)
    {
        _poker = poker;
    }

    // from Notification
    public String getAnnouncement ()
    {
        return MessageBundle.tcompose("m.profile_poked", _poker.toString(), _poker.getId());
    }

    protected MemberName _poker;
}
