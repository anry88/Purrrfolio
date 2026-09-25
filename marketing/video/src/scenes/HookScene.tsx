import {
  AbsoluteFill,
  CanvasImage,
  Easing,
  Interactive,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";

export const HookScene: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <AbsoluteFill
      style={{
        overflow: "hidden",
        background: "#17102c",
        color: "#fffaf0",
        fontFamily: "Arial, Helvetica, sans-serif",
      }}
    >
      <CanvasImage
        name="Sleepy background"
        src={staticFile("cards/sleepy.png")}
        width={1080}
        height={1920}
        fit="cover"
        style={{
          position: "absolute",
          inset: 0,
          opacity: 0.52,
          filter: "blur(18px) saturate(1.15)",
          scale: interpolate(frame, [0, 90], [1.16, 1.28], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            output: "perceptual-scale",
          }),
        }}
      />
      <Interactive.Div
        name="Dark overlay"
        style={{
          position: "absolute",
          inset: 0,
          background:
            "linear-gradient(180deg, rgba(17, 12, 36, 0.38) 0%, rgba(17, 12, 36, 0.86) 58%, #17102c 100%)",
        }}
      />
      <Interactive.Div
        name="Brand label"
        style={{
          position: "absolute",
          top: 135,
          left: 90,
          right: 90,
          textAlign: "center",
          fontSize: 46,
          fontWeight: 900,
          letterSpacing: 8,
          color: "#ffd88b",
          opacity: interpolate(frame, [0, 12], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        PURRRFOLIO
      </Interactive.Div>
      <Interactive.Div
        name="Hook headline"
        style={{
          position: "absolute",
          top: 910,
          left: 90,
          right: 90,
          textAlign: "center",
          fontSize: 112,
          lineHeight: 0.98,
          fontWeight: 900,
          letterSpacing: -5,
          textShadow: "0 12px 36px rgba(0, 0, 0, 0.58)",
          opacity: interpolate(frame, [8, 24], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          translate: interpolate(frame, [8, 24], ["0px 70px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        What’s inside a Fluffy Pack?
      </Interactive.Div>
      <Interactive.Div
        name="Hook subtitle"
        style={{
          position: "absolute",
          top: 1320,
          left: 110,
          right: 110,
          textAlign: "center",
          fontSize: 50,
          lineHeight: 1.25,
          fontWeight: 700,
          color: "rgba(255, 250, 240, 0.9)",
          opacity: interpolate(frame, [24, 38], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Three cozy cat-card reveals in 15 seconds.
      </Interactive.Div>
      <Interactive.Div
        name="Swipe cue"
        style={{
          position: "absolute",
          top: 1645,
          left: 280,
          right: 280,
          minHeight: 94,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          borderRadius: 999,
          background: "rgba(255, 216, 139, 0.94)",
          color: "#211532",
          fontSize: 46,
          fontWeight: 900,
          opacity: interpolate(frame, [38, 52], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [38, 52], [0.8, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 170 }),
            output: "perceptual-scale",
          }),
        }}
      >
        OPEN THE PACK 🐾
      </Interactive.Div>
    </AbsoluteFill>
  );
};
