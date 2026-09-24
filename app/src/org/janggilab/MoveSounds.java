package org.janggilab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.util.HashSet;
import java.util.Set;

/** Short, locally generated effects; no network or speech service required. */
final class MoveSounds {
    private final SoundPool pool;
    private final Set<Integer> loaded=new HashSet<>();
    private final int move, capture, check;
    boolean enabled;
    MoveSounds(Context context) {
        enabled=context.getSharedPreferences("janggi",0).getBoolean("sounds",true);
        pool=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p,id,status)->{if(status==0)synchronized(loaded){loaded.add(id);}});
        move=load(context,"move");capture=load(context,"capture");check=load(context,"check");
    }
    private int load(Context c,String name){return pool.load(c,c.getResources().getIdentifier(name,"raw",c.getPackageName()),1);}
    private void play(int id){synchronized(loaded){if(enabled&&loaded.contains(id))pool.play(id,.7f,.7f,1,0,1f);}}
    void move(boolean captured){play(captured?capture:move);}
    void check(){play(check);}
    void pause(){pool.autoPause();}
    void close(){pool.release();synchronized(loaded){loaded.clear();}}
}
