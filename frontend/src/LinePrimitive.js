import { positionsBox } from './helpers/positions';

class LineRenderer {
    constructor(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => {
            const ctx = scope.context;
            
            console.log('LineRenderer.draw() called, lines count:', this._data.lines.length);

            this._data.lines.forEach((line, idx) => {
                if (line.x1 === null || line.x2 === null || line.y1 === null || line.y2 === null) {
                    console.log(`Line ${idx + 1} skipped (null coordinates):`, line);
                    return;
                }
                
                console.log(`Drawing line ${idx + 1}:`, line);

                const x1 = line.x1 * scope.horizontalPixelRatio;
                const y1 = line.y1 * scope.verticalPixelRatio;
                const x2 = line.x2 * scope.horizontalPixelRatio;
                const y2 = line.y2 * scope.verticalPixelRatio;

                // Draw line
                ctx.strokeStyle = line.color || '#2196F3';
                ctx.lineWidth = (line.lineWidth || 1) * scope.horizontalPixelRatio;
                
                // Set line style
                if (line.lineStyle === 'dashed') {
                    ctx.setLineDash([5 * scope.horizontalPixelRatio, 5 * scope.horizontalPixelRatio]);
                } else if (line.lineStyle === 'dotted') {
                    ctx.setLineDash([2 * scope.horizontalPixelRatio, 2 * scope.horizontalPixelRatio]);
                } else {
                    ctx.setLineDash([]);
                }
                
                ctx.beginPath();
                ctx.moveTo(x1, y1);
                ctx.lineTo(x2, y2);
                ctx.stroke();

                // Draw label if provided
                if (line.label) {
                    ctx.fillStyle = line.color || '#2196F3';
                    ctx.font = `${10 * scope.verticalPixelRatio}px Arial`;
                    ctx.textBaseline = 'bottom';
                    ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
                    ctx.shadowBlur = 2 * scope.verticalPixelRatio;
                    
                    // Position label at the start of the line, slightly above
                    ctx.fillText(
                        line.label,
                        x1 + 4 * scope.horizontalPixelRatio,
                        y1 - 4 * scope.verticalPixelRatio
                    );
                    ctx.shadowBlur = 0;
                }
                
                // Reset line dash
                ctx.setLineDash([]);
            });
        });
    }
}

class LinePaneView {
    constructor(source) {
        this._source = source;
        this._lines = []; // Initialize empty array
    }

    update() {
        const series = this._source._series;
        const timeScale = this._source._chart.timeScale();
        const chartData = this._source._chartData;
        
        console.log('LinePaneView.update() called, linesData:', this._source._linesData);
        
        this._lines = this._source._linesData.map((line, idx) => {
            let x1 = timeScale.timeToCoordinate(line.time1);
            let x2 = timeScale.timeToCoordinate(line.time2);
            const y1 = series.priceToCoordinate(line.price1);
            const y2 = series.priceToCoordinate(line.price2);

            // Fallback: If timeToCoordinate returns null, use logical indices
            if ((x1 === null || x2 === null) && chartData && chartData.length > 0) {
                console.log(`Line ${idx + 1}: timeToCoordinate returned null, using logical indices`);
                
                let logical1 = null;
                let logical2 = null;
                
                // Find logical indices for the line times
                for (let i = 0; i < chartData.length; i++) {
                    if (chartData[i].time >= line.time1 && logical1 === null) {
                        logical1 = i;
                    }
                    if (chartData[i].time >= line.time2) {
                        logical2 = i;
                        break;
                    }
                }
                
                // Handle edge cases
                if (logical2 === null && line.time2 > chartData[chartData.length - 1].time) {
                    logical2 = chartData.length - 1;
                }
                if (logical1 === null && line.time1 < chartData[0].time) {
                    logical1 = 0;
                }
                
                // Convert logical indices to coordinates
                if (logical1 !== null) {
                    x1 = timeScale.logicalToCoordinate(logical1);
                }
                if (logical2 !== null) {
                    x2 = timeScale.logicalToCoordinate(logical2);
                }
                
                console.log(`Line ${idx + 1} logical fallback:`, { logical1, logical2, x1, x2 });
            }

            console.log(`Line ${idx + 1} final coordinates:`, {
                time1: line.time1,
                time2: line.time2,
                price1: line.price1,
                price2: line.price2,
                x1,
                x2,
                y1,
                y2,
            });

            return {
                x1,
                x2,
                y1,
                y2,
                color: line.color,
                lineWidth: line.lineWidth,
                lineStyle: line.lineStyle,
                label: line.label,
            };
        });
        
        console.log('LinePaneView.update() complete, lines:', this._lines);
    }

    renderer() {
        console.log('LinePaneView.renderer() called, returning renderer with lines:', this._lines?.length || 0);
        return new LineRenderer({
            lines: this._lines || [],
        });
    }
}

export class LinePrimitive {
    constructor(chart, series, linesData, chartData) {
        this._chart = chart;
        this._series = series;
        this._linesData = linesData;
        this._chartData = chartData; // Main chart data for logical coordinate fallback
        this._paneViews = [new LinePaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    autoscaleInfo(startTimePoint, endTimePoint) {
        // Include line prices in autoscale
        let minPrice = Infinity;
        let maxPrice = -Infinity;

        this._linesData.forEach(line => {
            const minLinePrice = Math.min(line.price1, line.price2);
            const maxLinePrice = Math.max(line.price1, line.price2);
            if (minLinePrice < minPrice) minPrice = minLinePrice;
            if (maxLinePrice > maxPrice) maxPrice = maxLinePrice;
        });

        if (minPrice === Infinity || maxPrice === -Infinity) {
            return null;
        }

        return {
            priceRange: {
                minValue: minPrice,
                maxValue: maxPrice,
            },
        };
    }
}

