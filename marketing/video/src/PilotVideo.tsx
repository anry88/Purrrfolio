import { TransitionSeries, linearTiming } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { BoxieScene } from "./scenes/BoxieScene";
import { CtaScene } from "./scenes/CtaScene";
import { GardenerScene } from "./scenes/GardenerScene";
import { HookScene } from "./scenes/HookScene";
import { SleepyScene } from "./scenes/SleepyScene";

export const PilotVideo: React.FC = () => {
  return (
    <TransitionSeries>
      <TransitionSeries.Sequence durationInFrames={90} name="Hook">
        <HookScene />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition
        presentation={fade()}
        timing={linearTiming({ durationInFrames: 12 })}
      />
      <TransitionSeries.Sequence durationInFrames={90} name="Sleepy reveal">
        <SleepyScene />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition
        presentation={fade()}
        timing={linearTiming({ durationInFrames: 12 })}
      />
      <TransitionSeries.Sequence durationInFrames={90} name="Tiny Gardener reveal">
        <GardenerScene />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition
        presentation={fade()}
        timing={linearTiming({ durationInFrames: 12 })}
      />
      <TransitionSeries.Sequence durationInFrames={90} name="Boxie reveal">
        <BoxieScene />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition
        presentation={fade()}
        timing={linearTiming({ durationInFrames: 12 })}
      />
      <TransitionSeries.Sequence durationInFrames={138} name="Call to action">
        <CtaScene />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  );
};
