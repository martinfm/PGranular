# PGranular


PGranular is a graphical front end to [SuperCollider](http://supercollider.github.io) 
granular synthesis, that I created for my own sound experiments. It is a GUI based aid
to make the process of exploring granular synthesis easier, but it's also conceived as
an instrument to perform straight away with the sounds you create. It can be used with
mouse, keyboard or MIDI for live performaces or automation via a Daw.

![PGranular screenshot](https://martinfm.files.wordpress.com/2015/04/pgranular1.jpg)

Start by importing into PGranular a wave file from your hardisk or by
**recording your own audio into PGranular** in real time with a microphone. 

Then create up to 10 selections, which represent your grains; for each selection you
can tweak synthesis parameters such as size, position in the audio file, amplitude,
pan, duration, pitch, randomization and a number of LFO's.

Finally play your selections either in a continuous loop or using a MIDI device.
You can also assign different selections to these two modes and play them at the same time!
Furthermore the MIDI mode lets you play your selections **like a keyboard synthesizer** or
like a **percussive element** so as to make your own granular-flovoured percussive lines.

As the sound plays, you can adjust the synthesis parameters in real time via mouse,
keyboard or MIDI and you can **record new audio** so that the granular synthesis
will continue playing according to your parameter settings, but with **new audio content underneath**!

You can easily persist your synthesis paramteres settings and retrieve them later, so as not to
loose your work once your shut your computer down.

PGranular leverages all the power of the SuperCollider synthesis and it is written
in 100% sclang so you can run it straight from your favourite SuperCollider IDE/editor. 

## Installation


 * Install SuperCollider together with the Synthesis Plugins and Extensions
 (PGranular depends on [*TGrains2*](http://doc.sccode.org/Classes/TGrains2.html) ).
 Both are available on the [SuperCollider download page](http://supercollider.github.io/download.html);
 * Drop the *PGranularExtensions* folder in the extensions directory. You can find out
 your extensions directory location by running `Platform.userExtensionDir` or 
 `Platform.systemExtensionDir` in SuperCollider, respectively for the user
 specific and system extensions directories;
 * Run the code in *PGranular.scd*. This file can be placed anywhere
 you like, as long as *PGranularSynths.scd* is placed in the same directory. 

PGranular has been tested on Windows only.
