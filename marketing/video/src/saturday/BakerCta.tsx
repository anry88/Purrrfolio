import { Img, staticFile } from "remotion";
import { BakerStage, Headline } from "./BakerStage";

export const BakerCta: React.FC = () => (
  <BakerStage>
    <Headline>{"Collect cat cards\nin Telegram."}</Headline>
    <Img
      src={staticFile("cards/baker.png")}
      style={{
        position: "absolute",
        left: 310,
        top: 555,
        width: 460,
        height: 690,
        rotate: "-5deg",
        boxShadow: "0 30px 70px #0006",
        borderRadius: 26,
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 88,
        right: 88,
        top: 1370,
        fontSize: 54,
      }}
    >
      Find
    </div>
    <div
      style={{
        position: "absolute",
        left: 88,
        right: 88,
        top: 1460,
        fontSize: 74,
        fontWeight: 900,
        color: "#f4c494",
      }}
    >
      @PurrrfolioBot.
    </div>
  </BakerStage>
);
