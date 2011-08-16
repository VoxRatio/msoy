//
// $Id$

package com.threerings.msoy.notify.data {

import com.threerings.io.ObjectInputStream;
import com.threerings.util.MessageBundle;

import com.threerings.orth.notify.data.Notification;

import com.threerings.msoy.data.all.MemberName;

public class PokeNotification extends Notification
{
    override public function getAnnouncement () :String
    {
        return MessageBundle.tcompose("m.profile_poked", _poker.toString(), _poker.getId());
    }

    override public function readObject (ins :ObjectInputStream) :void
    {
        super.readObject(ins);
        _poker = MemberName(ins.readObject());
    }

    protected var _poker :MemberName;
}
}
