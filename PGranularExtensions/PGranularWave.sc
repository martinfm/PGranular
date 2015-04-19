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

PGranularWave  {
  classvar s_lfoSpec;
  classvar s_selectionColors;
  var m_action;
  var m_numSelections;
  var m_randomStartArray;
  var m_randomPanArray;
  var m_durFactorArray;
  var m_pitchFactorArray;
  var m_reverseArray;
  var m_amplitudeArray;
  var m_panArray;
  var m_controlSpecs;
  var m_sampleRate;
  var m_samplesPerPixel;
  var m_waveType;
  var m_wavePath;
  var m_waveData;
  var m_posLFOs;
  var m_ampLFOs;
  var m_pitchLFOs;
  var m_maxPitchLFOs;
  var <>view;


  /* the following variables mirror the view's variables
  they are added to allow midi messages to trigger sound
  without going through the defer call */
  var m_currentSelectionIndex;
  var m_selections;

  *initClass {
    s_lfoSpec = ControlSpec(0,20,warp:'lin',default:0);
  }

  *new { arg parent, bounds, numSelections = 10;
    if(numSelections < 2, {
      "Warning: minimum 2 selections required,
      created PGranularFileView with 2 selections ".postln;
    });
    ^super.new.initGranularWave(parent, bounds, max(2,numSelections));
  }

  initGranularWave {arg parent, bounds, numSelections;
    var alpha = 130;
    var click = PGranularMouseClick.new;
    /* init mouse actions */
    view  = SoundFileView.new(parent,bounds)
    .mouseDownAction_({arg view, x, y, modifiers, button, numClicks;
      click.down(button); // keep track of the clicks to handle the motion
    })
    .mouseUpAction_({arg view, x, y, modifiers, button;
      click.up(button);
      if(modifiers.isCtrl and: button == 0){
        /* move the current selection upon ctrl + right click to the clicked position */
        this.setSelectionStart(m_currentSelectionIndex,view.timeCursorPosition);
      };
    })
    .mouseMoveAction_({arg view, x, y, modifiers;

      case
      {modifiers.isCtrl and: click.left}{
        this.setSelectionStart(m_currentSelectionIndex,view.timeCursorPosition);
      }
      {click.left}{
        m_selections.put(m_currentSelectionIndex,view.selection(m_currentSelectionIndex));
        this.changed('selectionCoord',[m_currentSelectionIndex,m_selections.at(m_currentSelectionIndex)]);
      }
      { modifiers.isCtrl and: modifiers.isShift and: click.right}{ // zoom
        /* adjust the samplePerPixel as the viewFrames have changed now */
        m_samplesPerPixel = view.viewFrames / view.bounds.width;
      }
      {modifiers.isCtrl and: modifiers.isShift.not}{ // scroll

      };
    });


    m_sampleRate = 44100;

    m_numSelections = numSelections;

    m_randomStartArray = 0 ! numSelections;
    m_randomPanArray = 0 ! numSelections;
    m_durFactorArray = 1 ! numSelections;
    m_pitchFactorArray = 1 ! numSelections;
    m_reverseArray = false ! numSelections;
    m_amplitudeArray = 1 ! numSelections;
    m_panArray = 0 ! numSelections;
    m_posLFOs = 0 ! numSelections;
    m_ampLFOs = 0 ! numSelections;
    m_pitchLFOs = 0 ! numSelections;
    m_maxPitchLFOs = 1 ! numSelections;

    m_currentSelectionIndex = 0;
    m_selections = Array.fill(numSelections,{[0,0]}); // init with empty selections

    s_selectionColors = [
      Color.new255(37,37,186,alpha),
      Color.new255(129,112,197,alpha),
      Color.new255(221,224,209,alpha),
      Color.new255(160,169,5,alpha),
      Color.new255(241,250,86,alpha),
      Color.new255(49,68,40,alpha),
      Color.new255(5,163,17,alpha),
      Color.new255(121,43,4,alpha),
      Color.new255(250,132,75,alpha),
      Color.new255(219,149,18,alpha),
      // Color.new255(84,84,243,alpha)Color.blue(alpha:alpha/240),
    ];

    s_selectionColors.do({arg item, i;
      view.setSelectionColor(i,item);
    });

    m_controlSpecs = IdentityDictionary[
      'currentSelection' -> [0,9,\lin,1,0].asSpec,
      'randomStart' -> [0,1,\lin,0.01,0,0].asSpec,
      'randomPan' -> [0,1,\lin,0.01,0,0].asSpec,
      'durFactor' -> [0,20,\lin,0.01,1].asSpec,
      'pitchFactor' -> ControlSpec(0.1,5,\exp,0.01,1),
      'amplitude' -> ControlSpec(0.ampdb, 3.ampdb, \db, units: " dB",default:1),
      'pan' -> ControlSpec(-1, 1, 'linear', 0.0, 0, "", default:0),
      'selectionCoord' -> ControlSpec(0,view.bounds.width,\lin,1,0,"pixel"),
      'selectionStart' -> ControlSpec(0,view.bounds.width,\lin,1,0,"pixel"),
      'selectionSize' -> ControlSpec(0,view.bounds.width,\lin,1,0,"pixel"),
      'posLFO' -> s_lfoSpec,
      'pitchLFO' -> s_lfoSpec,
      'ampLFO' -> s_lfoSpec,
      'maxPitchLFO' -> ControlSpec.new(-1, 1, 'lin', 0, 0)
    ];
  }

  flashSelection { arg index, flashTime;
    view.setSelectionColor(index,Color.white);

    AppClock.sched(flashTime,{
      view.setSelectionColor(index,s_selectionColors[index]);
      nil; // stop the task from being rescheduled
    });
  }

  numSelections {
    ^m_numSelections;
  }

  setSelection {arg index, selection;
    if(index >= this.numSelections,{
      ^this;
    });

    m_selections.put(index,selection);

    {
      view.setSelection(index,selection);
      /* sets the time position if the index is the current selection one */
      if(index == view.currentSelection(index),{
        view.timeCursorPosition_(m_selections.at(0));
      });
    }.defer;

    this.changed('selectionCoord',[index,selection]);
  }

  /* returns array [start,end] */
  selection{ arg index;
    ^m_selections.at(index);
  }

  /* set selection in frames */
  setSelectionStart {arg index,frame;
    m_selections.at(index).put(0,frame);

    {
      view.setSelectionStart(index,frame);
      /* sets the time position if the index is the current selection one */
      if(index == view.currentSelection(index),{
        view.timeCursorPosition_(frame);
      });
    }.defer;
    this.changed('selectionStart',[index,frame]);
  }

  selectionStart{arg index;
    ^m_selections.at(index).at(0);
  }

  zoom{ arg factor;
    view.zoom(factor);
    /* adjust the samplePerPizel as the viewFrames have changed now */
    m_samplesPerPixel = view.viewFrames / view.bounds.width;
  }

  /* frames is th size of the selection */
  setSelectionSize {arg index, frames;
    m_selections.at(index).put(1,frames);

    {
      view.setSelectionSize(index, frames);
    }.defer;

    this.changed('selectionSize',[index, frames]);
  }

  selectionSize {arg index;
    ^m_selections.at(index).at(1);
  }

  currentSelection_ { arg selectionIndex;
    m_currentSelectionIndex = selectionIndex;

    {
      view.currentSelection_(selectionIndex);
      view.timeCursorPosition_(view.selectionStart(selectionIndex));
    }.defer;

    this.changed('currentSelection',[selectionIndex]);
  }

  currentSelection {
    ^m_currentSelectionIndex;
  }

  randomStart {arg selectionIndex;
    ^m_randomStartArray.at(selectionIndex);
  }

  randomStart_ {arg selectionIndex, value;
    m_randomStartArray.put(selectionIndex,value);
    this.changed('randomStart',[selectionIndex,value]);
  }

  randomPan { arg selectionIndex;
    ^m_randomPanArray.at(selectionIndex);
  }

  randomPan_ {arg selectionIndex, value;
    m_randomPanArray.put(selectionIndex,value);
    this.changed('randomPan',[selectionIndex,value]);
  }

  durFactor {arg selectionIndex;
    ^m_durFactorArray.at(selectionIndex);
  }

  durFactor_ {arg selectionIndex, value;
    m_durFactorArray.put(selectionIndex, value);
    this.changed('durFactor',[selectionIndex, value]);
  }

  pitchFactor {arg selectionIndex;
    ^m_pitchFactorArray.at(selectionIndex);
  }

  pitchFactor_ {arg selectionIndex,value;
    m_pitchFactorArray.put(selectionIndex,value);
    this.changed('pitchFactor',[selectionIndex,value]);
  }

  reversed_ {arg selectionIndex, reversed;
    m_reverseArray.put(selectionIndex, reversed);
    this.changed('reverse',[selectionIndex,reversed]);
  }

  reversed {arg selectionIndex;
    ^m_reverseArray.at(selectionIndex);
  }

  amplitude { arg selectionIndex;
    ^m_amplitudeArray.at(selectionIndex)
  }

  amplitude_ {arg selectionIndex, value;
    m_amplitudeArray.put(selectionIndex,value);
    this.changed('amplitude',[selectionIndex,value]);
  }

  pan {arg selectionIndex;
    ^m_panArray.at(selectionIndex);
  }

  pan_ { arg selectionIndex, value;
    m_panArray.put(selectionIndex, value);
    this.changed('pan',[selectionIndex, value]);
  }

  posLFO { arg index;
    ^m_posLFOs.at(index);
  }

  posLFO_ { arg selectionIndex, value;
    m_posLFOs.put(selectionIndex, value);
    this.changed('posLFO',[selectionIndex, value]);
  }

  ampLFO { arg index;
    ^m_ampLFOs.at(index);
  }

  ampLFO_ { arg selectionIndex, value;
    m_ampLFOs.put(selectionIndex, value);
    this.changed('ampLFO',[selectionIndex, value]);
  }

  pitchLFO { arg index;
    ^m_pitchLFOs.at(index);
  }

  pitchLFO_ { arg selectionIndex, value;
    m_pitchLFOs.put(selectionIndex, value);
    this.changed('pitchLFO',[selectionIndex, value]);
  }

  maxPitchLFO { arg selectionIndex;
    ^m_maxPitchLFOs.at(selectionIndex);
  }

  maxPitchLFO_{ arg selectionIndex, value;
    m_maxPitchLFOs.put(selectionIndex, value);
    this.changed('maxPitchLFO',[selectionIndex, value]);
  }

  sampleRate {
    ^m_sampleRate;
  }

  /**
  returns how many samples are in a pizel
  */
  samplesPerPixel {
    ^m_samplesPerPixel;
  }

  secondsPerPixel {
    ^this.samplesPerPixel/m_sampleRate;
  }

  selectionDuration {arg selectionIndex;
    var duration = this.selectionSize(selectionIndex)/this.sampleRate;
    /* max between the duration obtained and one pixel duration */
    /*  avoids dividing by zero if the duration is 0 */
    //^duration.max(this.secondsPerPixel);
    ^duration;
  }

  /* function that clips the argument in the number of frames */
  withinNumFrames { arg n;
    ^n.clip(0,view.numFrames);
  }

  controlSpecOf {arg param;
    ^m_controlSpecs[param];
  }

  controlSpecKeys {
    ^m_controlSpecs.keys;
  }

  loadFile {arg file;
    view.soundfile_(file);
    view.read(0, file.numFrames);
    m_sampleRate = file.sampleRate;
    /* how many samples per pixel. When the ifle is loaded this is queal
    to the total number of frames*/
    m_samplesPerPixel = view.viewFrames / view.bounds.width;

    m_waveType = 'file';
    m_wavePath = file.path;
    m_waveData = nil;
  }

  loadData {arg data;
    view.data_(data);
    view.soundfile_(nil);
    view.read(0, data.size);
    m_sampleRate = 44100;
    m_samplesPerPixel = view.viewFrames / view.bounds.width;
    m_waveType = 'record';
    m_wavePath = nil;
    m_waveData = data;
  }

  saveData {arg path;
    var soundFile;

    if(m_waveData.isNil){
      Error("No recorded data to save").throw;
    };

    soundFile = SoundFile.new.headerFormat_("WAV").numChannels_(1);
    if(soundFile.openWrite(path) == false){
      Error("Could not write to "++path).throw;
    };

    soundFile.writeData(m_waveData);
    soundFile.close;

    /* sets the variable to make it as if a file was open */
    m_waveType = 'file';
    m_wavePath = path;
    m_waveData = nil;
    ("Recording written to "++path).postln;
  }

  /**
  returns :
  - 'file' if the audio is stored in a file
  - 'record' if tha audio has been recorded on the fly and is not stored on a file
  - nil if there is no audio loaded
  */
  waveType {
    ^m_waveType;
  }

  wavePath {
    ^m_wavePath;
  }

  parameters {
    ^['currentSelection', 'randomStart', 'randomPan', 'durFactor', 'pan',
      'amplitude', 'selectionStart', 'reverse', 'selectionSize',
      'pitchFactor', 'posLFO', 'ampLFO', 'pitchLFO', 'maxPitchLFO'];

  }

  /* used for MIDI, contains the additional parameter 'selectioinStartAdjust' */
  controlFunz { arg param, maxval = 127, selection = -1;
    switch(param,
      'currentSelection',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.currentSelection_(s,m_controlSpecs[param].map(val/maxval));
        }
      },
      'randomStart',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.randomStart_(s,m_controlSpecs[param].map(val/maxval));
        }
      },
      'randomPan',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.randomPan_(s,m_controlSpecs[param].map(val/maxval));
        }
      },
      'durFactor',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.durFactor_(s,m_controlSpecs[param].map(val/maxval));
        }
      },
      'reverse',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.reversed_(s,val > 0);
        }
      },
      'pitchFactor',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.pitchFactor_(s,m_controlSpecs[param].map(val/maxval));
        }
      },
      'pan',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.pan_(s,m_controlSpecs[param].map(val/maxval));
        }
      },
      'amplitude',{
        ^{arg val, num, chan, src;
          var s = (selection == -1).if({this.currentSelection},{selection});
          this.amplitude_(s,m_controlSpecs[param].map(val/maxval).dbamp);
        }
      },
      'selectionStart',{
        ^{arg val, num, chan, src;
          if(m_waveType.notNil){
            var s = (selection == -1).if({this.currentSelection},{selection});
            this.setSelectionStart(s,m_controlSpecs[param].map(val/maxval)*this.samplesPerPixel);
          };
        }
      },
      'selectionStartAdjust',{
        var previousVal = 0;
        ^{arg val, num, chan, src;
          if(m_waveType.notNil){
            var s = (selection == -1).if({this.currentSelection},{selection});
            var adjust = val - previousVal;
            if(adjust != 0){
              previousVal = val;
              /* if you turn right it will shift the selection right */
              /* if you turn left it will shift the selection left */
              if(adjust > 0){
                this.setSelectionStart(s,this.selectionStart(s) + this.samplesPerPixel );
              }{
                this.setSelectionStart(s,this.selectionStart(s) - this.samplesPerPixel );
              };
            };
          };
        }
      },
      'selectionSize',{
        ^{arg val, num, chan, src;
          if(m_waveType.notNil){
            var s = (selection == -1).if({this.currentSelection},{selection});
            this.setSelectionSize(s,m_controlSpecs[param].map(val/maxval)/4*this.samplesPerPixel);
            // 1/4 of the whole screen
          };
        }
      },
      'posLFO',{
        ^{arg val, num, chan, src;
          if(m_waveType.notNil){
            var s = (selection == -1).if({this.currentSelection},{selection});
            this.posLFO_(s,m_controlSpecs[param].map(val/maxval));
          };
        }
      },
      'ampLFO',{
        ^{arg val, num, chan, src;
          if(m_waveType.notNil,{
            var s = (selection == -1).if({this.currentSelection},{selection});
            this.ampLFO_(s,m_controlSpecs[param].map(val/maxval));
          });
        }
      },
      'pitchLFO',{
        ^{arg val, num, chan, src;
          if(m_waveType.notNil,{
            var s = (selection == -1).if({this.currentSelection},{selection});
            this.pitchLFO_(s,m_controlSpecs[param].map(val/maxval));
          });
        }
      },
      'maxPitchLFO',{
        ^{arg val, num, chan, src;
          if(m_waveType.notNil){
            var s = (selection == -1).if({this.currentSelection},{selection});
            this.maxPitchLFO_(s,m_controlSpecs[param].map(val/maxval));
          };
        }
      }
    );
  } // </controlFunz>

  reset { arg selectionIndex, param, default;

    /* if no default is passed reset to default of control spec */
    if(default.isNil){
      if(param == 'reverse'){
        /* reversed doean't hae a control spec */
        default = false;
      }{
        default = m_controlSpecs[param].default;
      };
    };

    switch(param,
      'currentSelection',{
        this.currentSelection_(default);
      },
      'randomStart',{
        this.randomStart_(selectionIndex, default);
      },
      'randomPan',{
        this.randomPan_(selectionIndex, default);
      },
      'durFactor',{
        this.durFactor_(selectionIndex, default);
      },
      'pitchFactor',{
        this.pitchFactor_(selectionIndex, default);
      },
      'pan',{
        this.pan_(selectionIndex, default);
      },
      'amplitude',{
        this.amplitude_(selectionIndex, default);
      },
      'selectionSize',{
        this.setSelectionSize(selectionIndex,default);
      },
      'selectionStart',{
        this.setSelectionStart(selectionIndex,default);
      },
      'reverse',{
        this.reversed_(selectionIndex,default);
      },
      'posLFO',{
        this.posLFO_(selectionIndex,default);
      },
      'ampLFO',{
        this.ampLFO_(selectionIndex,default);
      },
      'pitchLFO',{
        this.pitchLFO_(selectionIndex,default);
      },
      'maxPitchLFO',{
        this.maxPitchLFO_(selectionIndex,default);
      },
      { ("cannot reset parameter:"++param).postln;}
    );
  } // </reset>

  resetAll {
    (0..m_numSelections-1).do({ arg item;
      this.parameters.do(this.reset(item,_));
    });
  } // </resetAll>

  saveConfig { arg path, additionalConfig;
    /* creates an array of events with configuration*/
    var params;
    var config;

    if(m_wavePath.isNil){
      Error.new("Save recording to disk first").throw;
    };

    params = Array.fill(m_numSelections,{ arg index;
      ( // event with all the parameters
        currentSelection: index,
        randomStart: this.randomStart(index),
        randomPan: this.randomPan(index),
        durFactor: this.durFactor(index),
        pitchFactor: this.pitchFactor(index),
        amplitude: this.amplitude(index),
        pan: this.pan(index),
        selectionStart: this.selectionStart(index),
        selectionSize: this.selectionSize(index),
        reverse: this.reversed(index),
        posLFO: this.posLFO(index),
        ampLFO: this.ampLFO(index),
        pitchLFO: this.pitchLFO(index),
        maxPitchLFO: this.maxPitchLFO(index)
      );
    });

    config = [params, m_wavePath, additionalConfig];
    try {
      config.writeArchive(path);
    }{|e| e.throw};

    ("Configuration saved to "++path).postln;
  } // </saveConfig>

  loadConfig {arg path;
    /* config is an array of events, one foreach selection */
    var config;
    var params;
    var soundFile;
    var additionalConfig;
    var soundFilePath;

    try {
      config = Object.readArchive(path);
    }{|e| e.throw};

    /* config is an array [params, wavePath, additionalConfig] */
    params = config[0];
    soundFilePath = config[1];
    additionalConfig = config[2];

    params.do({ arg evt, selectionIndex;
      this.parameters().do{ |param|
        /* currentSelection is implicit in the arrays's index.
        It's there just to make the file more human readable */
        if(param != 'currentSelection'){
          var value = evt[param];
          if(value.notNil){
            this.reset(selectionIndex, param, value);
            ("setting config " +selectionIndex + param +value).postln;
          }{
            (param+"not found in configuration for selection"+selectionIndex).postln;
          }
        }
      };
    });

    /* read the sound file in */
    try {
      soundFile = SoundFile.new;
      soundFile.openRead(soundFilePath);
      this.loadFile(soundFile);
      soundFile.close;
    }{|e|
      soundFile.close;
      e.throw;
    };

    this.currentSelection(0);
    ("Configuration loaded from "++path).postln;

    ^config;
  } // </loadConfig>

} // </PGranularFileView>

PGranularMouseClick {
  var <>left;
  var <>right;
  var <>center;

  *new {
    var instance = super.new;

    instance.left = false;
    instance.center = false;
    instance.right = false;

    ^instance;
  }

  down { arg index;
    /* 0 = left, 1 = right, 2 = center */
    switch(index,
      0, {this.left = true},
      1, {this.right = true},
      2, {this.center = true}
    );
  }

  up { arg index;
    /* 0 = left, 1 = right, 2 = center */
    switch(index,
      0, {this.left = false},
      1, {this.right = false},
      2, {this.center = false}
    );
  }

}
