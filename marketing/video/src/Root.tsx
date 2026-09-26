import "./index.css";
import { BakerSaturdayVideo } from "./BakerSaturdayVideo";
import {
  BakerThumbnailCloseup,
  BakerThumbnailPoster,
} from "./saturday/BakerThumbnails";
import { Composition, Folder } from "remotion";
import { PilotVideo } from "./PilotVideo";
import { CozyHomeFridayVideo } from "./CozyHomeFridayVideo";
import { BoxieScene } from "./scenes/BoxieScene";
import { CtaScene } from "./scenes/CtaScene";
import { GardenerScene } from "./scenes/GardenerScene";
import { HookScene } from "./scenes/HookScene";
import { SleepyScene } from "./scenes/SleepyScene";

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="PurrrfolioBakerSaturday20260926"
        component={BakerSaturdayVideo}
        durationInFrames={960}
        fps={30}
        width={1080}
        height={1920}
      />
      <Composition
        id="BakerThumbnailCloseup"
        component={BakerThumbnailCloseup}
        durationInFrames={1}
        fps={30}
        width={1080}
        height={1920}
      />
      <Composition
        id="BakerThumbnailPoster"
        component={BakerThumbnailPoster}
        durationInFrames={1}
        fps={30}
        width={1080}
        height={1920}
      />
      <Composition
        id="PurrrfolioCozyHomeFriday20260925"
        component={CozyHomeFridayVideo}
        durationInFrames={450}
        fps={30}
        width={1080}
        height={1920}
      />
      <Composition
        id="PurrrfolioPilot001"
        component={PilotVideo}
        durationInFrames={450}
        fps={30}
        width={1080}
        height={1920}
      />
      <Folder name="Pilot001Scenes">
        <Composition
          id="Pilot001Hook"
          component={HookScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
        />
        <Composition
          id="Pilot001Sleepy"
          component={SleepyScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
        />
        <Composition
          id="Pilot001Gardener"
          component={GardenerScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
        />
        <Composition
          id="Pilot001Boxie"
          component={BoxieScene}
          durationInFrames={90}
          fps={30}
          width={1080}
          height={1920}
        />
        <Composition
          id="Pilot001Cta"
          component={CtaScene}
          durationInFrames={138}
          fps={30}
          width={1080}
          height={1920}
        />
      </Folder>
    </>
  );
};
