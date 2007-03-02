package com.threerings.msoy.client {

import flash.display.DisplayObjectContainer;

import com.threerings.util.CommandEvent;

import com.threerings.msoy.ui.MsoyList;

import com.threerings.msoy.data.SceneBookmarkEntry;

public class SceneBookmarkList extends MsoyList
{
    public function SceneBookmarkList (ctx :WorldContext)
    {
        super(ctx);
        includeInLayout = false;
    }

    override public function parentChanged (p :DisplayObjectContainer) :void
    {
        super.parentChanged(p);

        if (p != null) {
            updateListData();
        } else {
            dataProvider = null;
        }
    }

    protected function updateListData () :void
    {
        var entries :Array = _ctx.getMemberObject().recentScenes.toArray();
        entries.sort(function (o1 :Object, o2 :Object) :int {
            var sb1 :SceneBookmarkEntry = (o1 as SceneBookmarkEntry);
            var sb2 :SceneBookmarkEntry = (o2 as SceneBookmarkEntry);
            return int(sb1.lastVisit - sb2.lastVisit);
        });
        dataProvider = entries;
    }

    override protected function itemClicked (obj :Object) :void
    {
        var sbe :SceneBookmarkEntry = (obj as SceneBookmarkEntry);
        // this is a little tricky: we have to dispatch the go scene
        // prior to popping down, or our the go scene doesn't get to the right
        // place
        CommandEvent.dispatch(this, MsoyController.GO_SCENE, sbe.sceneId);
        CommandEvent.dispatch(this, MsoyController.SHOW_RECENT_SCENES, false);
    }
}
}
