import { AbsoluteFill, Audio, Sequence, staticFile } from "remotion";
import { BakerHunt } from "./saturday/BakerHunt";
import { BakerAnswer } from "./saturday/BakerAnswer";
import { BakerCta } from "./saturday/BakerCta";

export const BakerSaturdayVideo: React.FC = () => (
  <AbsoluteFill>
    <Audio
      src={staticFile("audio/generated/youtube-short-20260926-saturday-01.wav")}
      volume={0.7}
    />
    <Sequence durationInFrames={360}>
      <BakerHunt />
    </Sequence>
    <Sequence from={360} durationInFrames={420}>
      <BakerAnswer />
    </Sequence>
    <Sequence from={780} durationInFrames={180}>
      <BakerCta />
    </Sequence>
  </AbsoluteFill>
);
