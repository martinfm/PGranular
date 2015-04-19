
/*
 PGranular - a graphical front end to SuperCollider granular synthesis

 Copyright (C) 2015  Fiore Martin

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Graphical panel for PGranular midi controller.
init : inits the gui and the basic midi
dipose : disposes the gui and the midi
*/
PGranularMidiPanel{
  /* path of the MIDI mapping configuration file */
  classvar s_srcConfigPath;
  classvar s_disabledOut;
  /* the number of channels, it cannot exceed 16 */
  var m_wave;
  var m_lazyBuffer;
  var m_additionalActions;
  var m_midiCallbacks;
  var m_synths;
  var m_synthUpdateCallback;
  var m_window;
  var m_additionalControls;
  var m_connected;
  var m_midiInitialized;


  /* setter for callback when this panel is hidden/disposed/connecetd/disconnected */
  var >onClose;
  var >onConnect;
  var >onDisconnect;
  var >onDispose;
  var <disposed;

  *initClass {
    var ps = Platform.pathSeparator;
    s_srcConfigPath = (Platform.userExtensionDir ++ ps ++
      "PGranularExtensions" ++ ps ++ "PGranular_MIDI_config.yaml" );
    s_disabledOut = -1;
  }

  *new { arg grainView, additionalActions, lazyBuffer, additionalControls;
    ^super.new.init(grainView, additionalActions, lazyBuffer, additionalControls);
  }

  init { arg wave, additionalActions, lazyBuffer, additionalControls;
    m_wave = wave;
    m_additionalActions = additionalActions;
    m_lazyBuffer = lazyBuffer;
    m_synths = IdentityDictionary.new(10);
    m_midiCallbacks = List.new(20);
    m_additionalControls = additionalControls;

    m_connected = false;
    m_midiInitialized = false;
  }

  isSelectionEnabled{ arg index;
    ^m_additionalControls.midiEnabledSelections[index];
  }

  /* ---- GUI  ---- */
  showGUI {
    var model = this, channel, noteText,  connectBtn,  saveBtn,
    playTypeSelector, playChannelNum, srcConfigArea, midiDestSelector,
    srcEnableCheck, playEnableCheck, srcChanNum, view;

    if(m_midiInitialized.not){ m_midiInitialized = true; MIDIClient.init}; // init the MIDI clent once and for all

    m_window = Window("PGranular MIDI",Rect(400,300,490,325), resizable: false, scroll: true);
    m_window.onClose = {
      /* trigger the hide callback if any */
      if(onClose.notNil){onClose.(this)};
      m_window = nil;
    };

    view = m_window.asView ;
    view.decorator = FlowLayout(m_window.view.bounds).gap_(10 @ 11).margin_(10 @ 10);

    /* -- MIDI source --*/

    srcEnableCheck = CheckBox.new(view,20@20);

    StaticText(view,Rect(45, 10, 290, 20)).string_("MIDI CONTROL IN");


    srcChanNum = EZNumber(
      parent:view,
      bounds:120@22,
      label:"Channel:",
      controlSpec: ControlSpec.new(0,16,'lin',1,1),
      labelWidth:70,
      numberWidth: 50
    );

    srcConfigArea = TextView(view,Rect(10,0, m_window.bounds.width-40,170)).visible_(False);
    srcConfigArea.open(s_srcConfigPath);
    2.do({view.decorator.nextLine});

    /* -- PLAY -- */

    playEnableCheck = CheckBox.new(view,20@20);

    StaticText(view,Rect(45, 10, 80, 20)).string_("MIDI NOTE");

    playTypeSelector = EZPopUpMenu.new(
      parentView:view,
      bounds:200@22,
      label:"Type:",
      items: ['Piano', 'Percussion'],
      initAction:true,
      labelWidth:70
    );

    playChannelNum = EZNumber.new(
      parent:view,
      bounds:120@22,
      label:"Channel:",
      controlSpec: ControlSpec.new(0,16,'lin',1,1),
      labelWidth:70,
      numberWidth: 50
    );

    2.do({view.decorator.nextLine});

    /* button to start the MIDI */
    connectBtn = Button.new(view,100@22).states_([["Connect",nil,nil],["Disconnect",nil,nil]]);
    connectBtn.action = {|button|
      var connectionConfig = ();
      if(button.value == 1){ // connect
        block {|break|

          /* prepares the configuration for MIDI src, dst and play */
          if(srcEnableCheck.value){
            try{
              connectionConfig.srcConfig = srcConfigArea.string.parseYAML;
              connectionConfig.srcChan = srcChanNum.value;
            }{|e|
              PGranularMessageDialog.new("Could not parse MIDI source configuration");
              button.value = 0;
              break.value;
            }
          };

          if(playEnableCheck.value){
            connectionConfig.playType = playTypeSelector.item;
            connectionConfig.playChan = playChannelNum.value;
          };

          /* if any was enabled, connect the MIDI */
          if(connectionConfig.notEmpty){
            this.connect(connectionConfig);
          }

        }
      }{ // disconnect
        m_wave.removeDependant(this.waveCallback);

        m_synths.values.do(_.free);
        m_synths.clear;

        m_midiCallbacks.do(_.free); // free the registered callbacks

        MIDIClient.sources.do({ |src,i|
          MIDIIn.disconnect(i,src);
        });

        m_connected = false;

        if(onDisconnect.notNil){onDisconnect.(this)}
      }
    };

    /* sets the button according to the connection status */
    connectBtn.value = m_connected.if(1,0);


    saveBtn = Button.new(view, 100@22).states_([["Save Config",nil,nil]]);
    saveBtn.action = {
      var file, srcConfig, dstConfig;

      /* save the source configuration */
      try {
        srcConfig = srcConfigArea.string.parseYAML;
        protect{
          file = File(s_srcConfigPath,"w");

          file.putString(PGranularMidiPanel.toYaml(srcConfig));
        }{ file.close; };
        PGranularMessageDialog.new("MIDI source configuration saved");
      }{|e| PGranularMessageDialog.new("Could not save MIDI source configuration");
        e.postProtectedBacktrace};
    };// saveButton.action

    m_window.front; // actually show the window
  } // showGUI

  /* ----  MIDI ---- */

  connect { arg config ;
    MIDIIn.connectAll;
    //val, num, chan, src;


    /* --- install MIDI src control --- */

    /* install the controls embedded in the PGranularFileView */
    if(config.includesKey(\srcConfig)){
      var srcConfig = config.srcConfig;
      var srcChan = config.srcChan;

      /* check all parameters + special parameter selectionStartAdjust*/
      m_wave.parameters.add('selectionStartAdjust').do{|item,index|
        if(srcConfig[item.asString].notNil and: srcConfig[item.asString].isArray){
          srcConfig[item.asString].pairsDo({|selection, ccnum|
            if(selection == "current") { selection = -1}; // -1 for curent selection in ctrlFunz
            ccnum = ccnum.asInteger;
            srcChan = srcChan.asInteger;
            m_midiCallbacks.add(MIDIFunc.cc(
              m_wave.controlFunz(item,selection:selection.asInteger),
              ccnum,
              srcChan
            ));
          });
        };
      };

      /* install the controls for additional actions */
      m_additionalActions.keysValuesDo { |key, action|
        if(srcConfig[key.asString].notNil and: srcConfig[key.asString].isArray){
          srcConfig[key.asString].pairsDo({|selection, ccnum|
            // if(selection == "current") { selection = -1};
            ccnum = ccnum.asInteger;
            srcChan = srcChan.asInteger;
            m_midiCallbacks.add(MIDIFunc.cc(action,ccnum,srcChan));
          });
        };
      };
    }; // if(config.includesKey())

    /* --- install MIDI note  --- */

    if(config.includesKey(\playType)){
      var playType = config.playType;
      var playChan = config.playChan;

      if(playType == \Piano){
        m_midiCallbacks.add(MIDIFunc.noteOn(Message(this,\playPiano),chan:playChan));
        m_midiCallbacks.add(MIDIFunc.noteOff(Message(this,\stopPiano),chan:playChan));
      }{ // perc
        m_midiCallbacks.add(MIDIFunc.noteOn(Message(this,\playPerc),chan:playChan));
      };

      /* changes in the grainWave will affect sound generated by MIDI callbacks */
      m_wave.addDependant(this.waveCallback);
    };

    m_connected = true;
    if(onConnect.notNil){ onConnect.()};
  }

  dispose {
    if(m_window.notNil){
      m_window.close;
    };

    /* free all the callbacks installed for MIDI messages */
    m_midiCallbacks.do(_.free);
    m_midiCallbacks.clear;
    MIDIClient.disposeClient;

    /* trigger the dispose callback if any */
    if(onDispose.notNil){
      onDispose.value(this);
      onDispose = nil;
    };

    disposed = true;
  }

  waveCallback {
    if(m_synthUpdateCallback.isNil){
      m_synthUpdateCallback = {|wave, what, val|
        var i = val.at(0); // first element is the selection index

        if(wave.waveType.notNil){
          m_synths.keysValuesDo{|note, arrayOfsynths| // arrayOfSynths is one synth per selection
            arrayOfsynths[i].set(
              'freq',if(wave.selectionSize(i) == 0, {0}, {1/wave.selectionDuration(i)}),
              'pos',wave.selectionStart(i)/wave.sampleRate,
              'dur', wave.selectionDuration(i)*wave.durFactor(i),
              'randStart',wave.randomStart(i),
              'randPan',wave.randomPan(i),
              'pitch',if(wave.reversed(i),{wave.pitchFactor(i).neg},{m_wave.pitchFactor(i)}) *PGranularMidiPanel.calculateRatio(note),
              'amp',wave.amplitude(i),
              /* patch buggy behaviour : pan = 1 plays as it was pan = -1 */
              'pan',( (wave.pan(i)==1).if(0.99999,wave.pan(i))),
              'posLFO', wave.posLFO(i),
              'ampLFO', wave.ampLFO(i),
              'pitchLFO', wave.pitchLFO(i),
              'maxPitchLFO', wave.maxPitchLFO(i)
            );
          }
        };
      } // returned function
    };
    ^m_synthUpdateCallback
  }

  /* callback for Midifunc.noteOn plays trhe synths as a piano*/
  playPiano {arg veloc, note, chan, src;
    if(m_wave.waveType.isNil){
      "no file loaded".postln;
    }{

      /* play the synth related to this note and stores it in a dictionary
      to be retrieved in noteoff */
      m_synths.put(note,Array.fill(m_wave.numSelections,{ arg i;
        if(this.isSelectionEnabled(i)){
          var dedicatedOut = (m_additionalControls.dedicatedOuts[i] != s_disabledOut );
          Synth.new(dedicatedOut.if('granularPlayerMono', 'granularPlayer'),[
            'buffer',m_lazyBuffer.(),
            'freq',if(m_wave.selectionSize(i) == 0, 0, 1/m_wave.selectionDuration(i)),
            'loopLen', m_wave.selectionDuration(i)*m_wave.durFactor(i),
            'dur', m_wave.selectionDuration(i)*m_wave.durFactor(i),
            'pos',m_wave.selectionStart(i)/m_wave.sampleRate,
            'randStart',m_wave.randomStart(i),
            'randPan',m_wave.randomPan(i),
            'pitch',m_wave.pitchFactor(i)*PGranularMidiPanel.calculateRatio(note),
            'amp',m_wave.amplitude(i),
            'pan',m_wave.pan(m_wave.controlSpecOf('pan').unmap(i)),
            'posLFO', m_wave.posLFO(i),
            'ampLFO', m_wave.posLFO(i),
            'pitchLFO', m_wave.pitchLFO(i),
            'maxPitchLFO', m_wave.maxPitchLFO(i),
            'att',m_additionalControls.attack,
            'dec',m_additionalControls.decay,
            'out',dedicatedOut.if(m_additionalControls.dedicatedOuts[i], m_additionalControls.out)
          ]);
        }{
          {} // else return empty function
        }
        })
      );
    }
  }

  /* callback for Midifunc.noteOff plays trhe synths as a piano*/
  stopPiano {arg veloc, note, chan, src;
    var arrayOfSynths;

    if(m_wave.waveType.isNil){
      m_synths.clear;
    }{
      arrayOfSynths = m_synths.removeAt(note);
      arrayOfSynths.do {arg item; item.release}
    }
  }

  /* callback for Midifunc.noteOn plays the synths as a percussion */
  playPerc {arg veloc, note, chan, src;
    var i = note % 10;

    if(m_wave.waveType.isNil){
      "no file loaded".postln;
    }{
      if(this.isSelectionEnabled(i)){
        var dedicatedOut = (m_additionalControls.dedicatedOuts[i] != s_disabledOut );
        {m_wave.flashSelection(i,0.1)}.defer;

        Synth.new('granularBeat',[
          'buffer',m_lazyBuffer.(),
          'freq',if(m_wave.selectionSize(i) == 0, 0, 1/m_wave.selectionDuration(i)),
          'loopLen', m_wave.selectionDuration(i)*m_wave.durFactor(i),
          'dur', m_wave.durFactor(i),
          'pos',m_wave.selectionStart(i)/m_wave.sampleRate,
          'randStart',m_wave.randomStart(i),
          'randPan',m_wave.randomPan(i),
          'pitch',m_wave.pitchFactor(i),
          'amp',m_wave.amplitude(i),
          'pan',m_wave.pan(m_wave.controlSpecOf('pan').unmap(i)),
          'posLFO', m_wave.posLFO(i),
          'pitchLFO', m_wave.pitchLFO(i),
          'ampLFO', m_wave.ampLFO(i),
          'maxPitchLFO', m_wave.maxPitchLFO(i),
          'att',m_additionalControls.attack,
          'dec',m_additionalControls.decay,
          'out',dedicatedOut.if(m_additionalControls.dedicatedOuts[i], m_additionalControls.out)
        ]);
      }
    };
  }

  *toYaml{arg dictionary;
    var yaml = "";

    dictionary.keysValuesDo{|key, value|
      /* if there is no value (=nil) put an empty array */
      var v = (value.isNil).if([],value);
      yaml = yaml++ key.asString++":"+v.asString++"\n";
    };

    ^yaml;
  }

  *calculateRatio  {arg note;
    var diff = note - 60;

    if(diff.isNegative){
      var posDiff = diff.neg;
      var octaves = posDiff.div(12);
      var intervals = posDiff.mod(12);

      ^(1/2.pow(octaves)) / Scale.chromatic.ratios.wrapAt(intervals);
    }{
      var octaves = diff.div(12);
      var intervals = diff.mod(12);

      ^(2.pow(octaves) * Scale.chromatic.ratios[intervals]);
    }
  }

}

