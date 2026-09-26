import {
  AbsoluteFill,
  Img,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";

export const BakerStage: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => (
  <AbsoluteFill
    style={{
      background: "radial-gradient(ellipse at 40% 55%, #54312b, #1c1526 75%)",
      color: "#fff2d7",
      fontFamily: "Arial, sans-serif",
    }}
  >
    <div
      style={{
        position: "absolute",
        left: 86,
        top: 110,
        fontSize: 36,
        letterSpacing: 6,
        fontWeight: 700,
        color: "#f4c494",
      }}
    >
      PURRRFOLIO
    </div>
    {children}
  </AbsoluteFill>
);

export const BakeryArt: React.FC<{ answers?: boolean; fullCard?: boolean }> = ({
  answers = false,
  fullCard = false,
}) => {
  const frame = useCurrentFrame();
  return (
    <div
      style={{
        position: "absolute",
        left: fullCard ? 185 : 88,
        top: fullCard ? 460 : 520,
        width: fullCard ? 710 : 904,
        height: fullCard ? 1065 : 1020,
        overflow: "hidden",
        borderRadius: 42,
        boxShadow: "0 30px 80px #08050d88",
        border: "2px solid #eac8a13d",
      }}
    >
      <Img
        src={staticFile("cards/baker.png")}
        style={{
          width: "100%",
          height: "auto",
          transform: `scale(${fullCard ? 1 : interpolate(frame, [0, 350], [1, 1.025], { extrapolateRight: "clamp" })})`,
          transformOrigin: "50% 40%",
        }}
      />
      {answers &&
        [
          { x: 397, y: 341, n: "1" },
          { x: 480, y: 680, n: "2" },
          { x: 635, y: 183, n: "3" },
        ].map(({ x, y, n }, i) => (
          <div
            key={n}
            style={{
              position: "absolute",
              left: x - 58,
              top: y - 58,
              width: 116,
              height: 116,
              border: "7px solid #fff0b4",
              borderRadius: "50%",
              boxShadow: "0 0 0 4px #40242480",
              opacity: interpolate(frame, [i * 90, i * 90 + 12], [0, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
              }),
            }}
          >
            <div
              style={{
                position: "absolute",
                right: -20,
                top: -20,
                width: 48,
                height: 48,
                borderRadius: "50%",
                background: "#fff0b4",
                color: "#342029",
                textAlign: "center",
                lineHeight: "48px",
                fontSize: 32,
                fontWeight: 900,
              }}
            >
              {n}
            </div>
          </div>
        ))}
    </div>
  );
};

export const Headline: React.FC<{
  children: React.ReactNode;
  size?: number;
}> = ({ children, size = 88 }) => (
  <div
    style={{
      position: "absolute",
      top: 240,
      left: 88,
      right: 88,
      fontSize: size,
      lineHeight: 1.05,
      fontWeight: 900,
      whiteSpace: "pre-line",
    }}
  >
    {children}
  </div>
);
