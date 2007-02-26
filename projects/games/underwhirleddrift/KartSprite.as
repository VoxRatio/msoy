package {

import flash.display.Sprite;

import mx.core.MovieClipAsset;

public class KartSprite extends Sprite 
{
    public function KartSprite(kartCls :Class)
    {
        _kart = new kartCls();
        _kart.gotoAndStop(1);
    }

    /** Kart swf */
    [Embed(source='rsrc/mediumkart.swf#kart')]
    protected static const KART_MEDIUM :Class;

    protected var _kart :MovieClipAsset;
}
}
