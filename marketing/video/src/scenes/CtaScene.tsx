import {
  AbsoluteFill,
  CanvasImage,
  Easing,
  Interactive,
  interpolate,
  staticFile,
  useCurrentFrame,
} from "remotion";

export const CtaScene: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <AbsoluteFill
      style={{
        overflow: "hidden",
        background: "linear-gradient(155deg, #2b1847 0%, #10182f 52%, #213426 100%)",
        color: "#fffaf0",
        fontFamily: "Arial, Helvetica, sans-serif",
      }}
    >
      <Interactive.Div
        name="Gold glow"
        style={{
          position: "absolute",
          width: 980,
          height: 760,
          left: 50,
          top: 30,
          borderRadius: 999,
          background: "#ffca55",
          filter: "blur(220px)",
          opacity: 0.2,
        }}
      />
      <CanvasImage
        name="Sleepy card fan"
        src={staticFile("cards/sleepy.png")}
        width={360}
        height={540}
        fit="contain"
        style={{
          position: "absolute",
          top: 130,
          left: 120,
          rotate: interpolate(frame, [0, 20], ["-18deg", "-11deg"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 200 }),
          }),
          scale: interpolate(frame, [0, 20], [0.76, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 200 }),
            output: "perceptual-scale",
          }),
          filter: "drop-shadow(0 28px 30px rgba(0, 0, 0, 0.35))",
        }}
      />
      <CanvasImage
        name="Boxie card fan"
        src={staticFile("cards/boxie.png")}
        width={360}
        height={540}
        fit="contain"
        style={{
          position: "absolute",
          top: 130,
          right: 120,
          rotate: interpolate(frame, [0, 20], ["18deg", "11deg"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 200 }),
          }),
          scale: interpolate(frame, [0, 20], [0.76, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 200 }),
            output: "perceptual-scale",
          }),
          filter: "drop-shadow(0 28px 30px rgba(0, 0, 0, 0.35))",
        }}
      />
      <CanvasImage
        name="Tiny Gardener card fan"
        src={staticFile("cards/tiny-gardener.png")}
        width={400}
        height={600}
        fit="contain"
        style={{
          position: "absolute",
          top: 80,
          left: 340,
          scale: interpolate(frame, [5, 26], [0.78, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 190 }),
            output: "perceptual-scale",
          }),
          filter: "drop-shadow(0 34px 34px rgba(0, 0, 0, 0.42))",
        }}
      />
      <Interactive.Div
        name="CTA headline"
        style={{
          position: "absolute",
          top: 770,
          left: 85,
          right: 85,
          textAlign: "center",
          fontSize: 104,
          lineHeight: 0.98,
          fontWeight: 900,
          letterSpacing: -5,
          textShadow: "0 12px 36px rgba(0, 0, 0, 0.42)",
          opacity: interpolate(frame, [14, 30], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [14, 30], ["0px 46px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        Build your own Purrrfolio
      </Interactive.Div>
      <Interactive.Div
        name="CTA support"
        style={{
          position: "absolute",
          top: 1090,
          left: 110,
          right: 110,
          textAlign: "center",
          fontSize: 52,
          lineHeight: 1.22,
          fontWeight: 700,
          color: "rgba(255, 250, 240, 0.9)",
          opacity: interpolate(frame, [28, 42], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Collect cozy cat cards in Telegram.
      </Interactive.Div>
      <Interactive.Div
        name="Starter offer"
        style={{
          position: "absolute",
          top: 1285,
          left: 130,
          right: 130,
          minHeight: 100,
          borderRadius: 32,
          border: "3px solid rgba(255, 222, 147, 0.55)",
          background: "rgba(255, 255, 255, 0.1)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color: "#ffe29e",
          fontSize: 48,
          fontWeight: 900,
          opacity: interpolate(frame, [40, 54], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        3 starter packs • 3 cards each
      </Interactive.Div>
      <Interactive.Div
        name="Telegram button"
        style={{
          position: "absolute",
          top: 1480,
          left: 120,
          right: 120,
          minHeight: 150,
          borderRadius: 999,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#ffd36f",
          color: "#24152f",
          fontSize: 66,
          fontWeight: 900,
          boxShadow: "0 24px 60px rgba(0, 0, 0, 0.35)",
          opacity: interpolate(frame, [50, 66], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [50, 66, 95, 115], [0.82, 1, 1, 1.035], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 190 }),
            output: "perceptual-scale",
          }),
        }}
      >
        @PurrrfolioBot
      </Interactive.Div>
      <Interactive.Div
        name="CTA footnote"
        style={{
          position: "absolute",
          top: 1740,
          left: 90,
          right: 90,
          textAlign: "center",
          fontSize: 44,
          fontWeight: 700,
          color: "rgba(255, 250, 240, 0.72)",
          opacity: interpolate(frame, [62, 76], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Open • Collect • Share
      </Interactive.Div>
    </AbsoluteFill>
  );
};
