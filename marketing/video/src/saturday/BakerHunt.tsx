import { interpolate, useCurrentFrame } from "remotion";
import { BakerStage, BakeryArt, Headline } from "./BakerStage";

export const BakerHunt: React.FC = () => {
  const frame = useCurrentFrame();
  return (
    <BakerStage>
      <Headline>
        {frame < 120 ? "Can you spot\nthe paw prints?" : "Take a\ncloser look."}
      </Headline>
      <BakeryArt />
      <div
        style={{
          position: "absolute",
          left: 88,
          right: 88,
          top: 1610,
          height: 8,
          borderRadius: 10,
          background: "#ffffff22",
        }}
      >
        <div
          style={{
            height: "100%",
            borderRadius: 10,
            background: "#f4c494",
            width: `${interpolate(frame, [0, 359], [0, 100])}%`,
          }}
        />
      </div>
    </BakerStage>
  );
};
