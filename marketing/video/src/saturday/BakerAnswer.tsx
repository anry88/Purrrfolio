import { useCurrentFrame } from "remotion";
import { BakerStage, BakeryArt, Headline } from "./BakerStage";

export const BakerAnswer: React.FC = () => {
  const frame = useCurrentFrame();
  const summary = frame >= 270;
  return (
    <BakerStage>
      <Headline>
        {summary
          ? "Baker · Epic"
          : frame < 90
            ? "On the hat."
            : frame < 180
              ? "On the apron."
              : "On the jars."}
      </Headline>
      {summary && (
        <div
          style={{
            position: "absolute",
            top: 355,
            left: 88,
            fontSize: 44,
            color: "#d8b9f4",
          }}
        >
          Professions collection
        </div>
      )}
      <BakeryArt answers={!summary} fullCard={summary} />
    </BakerStage>
  );
};
