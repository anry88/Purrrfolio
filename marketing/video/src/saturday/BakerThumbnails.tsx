import { Img, staticFile } from "remotion";
import { BakerStage } from "./BakerStage";

export const BakerThumbnailCloseup: React.FC = () => (
  <BakerStage>
    <div
      style={{
        position: "absolute",
        left: 80,
        right: 80,
        top: 420,
        fontSize: 122,
        lineHeight: 0.98,
        fontWeight: 900,
      }}
    >
      SPOT THE
      <br />
      <span style={{ color: "#f4c494" }}>PAW PRINTS</span>
    </div>
    <div
      style={{
        position: "absolute",
        left: 80,
        top: 760,
        width: 920,
        height: 770,
        overflow: "hidden",
        borderRadius: 42,
      }}
    >
      <Img
        src={staticFile("cards/baker.png")}
        style={{ position: "absolute", width: 1350, left: -185, top: -390 }}
      />
      <div
        style={{
          position: "absolute",
          left: 334,
          top: 48,
          width: 145,
          height: 145,
          border: "9px solid #fff0b4",
          borderRadius: "50%",
        }}
      />
    </div>
  </BakerStage>
);

export const BakerThumbnailPoster: React.FC = () => (
  <BakerStage>
    <Img
      src={staticFile("cards/baker.png")}
      style={{
        position: "absolute",
        left: 205,
        top: 400,
        width: 670,
        height: 1005,
        rotate: "7deg",
        borderRadius: 38,
        boxShadow: "0 35px 80px #0008",
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 90,
        right: 90,
        top: 1380,
        padding: "20px 28px",
        background: "#f4c494",
        color: "#241729",
        borderRadius: 20,
        fontSize: 78,
        fontWeight: 900,
        textAlign: "center",
      }}
    >
      HIDDEN IN PLAIN SIGHT
    </div>
  </BakerStage>
);
